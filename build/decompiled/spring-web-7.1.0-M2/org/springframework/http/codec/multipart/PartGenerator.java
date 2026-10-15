package org.springframework.http.codec.multipart;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.reactivestreams.Subscription;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.core.io.buffer.DataBuffer.ByteBufferIterator;
import org.springframework.http.HttpHeaders;
import org.springframework.util.FastByteArrayOutputStream;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoSink;
import reactor.core.scheduler.Scheduler;
import reactor.util.context.Context;

final class PartGenerator extends BaseSubscriber<MultipartParser.Token> {
   private static final Log logger = LogFactory.getLog(PartGenerator.class);
   private final AtomicReference<PartGenerator.State> state = new AtomicReference<>(new PartGenerator.InitialState());
   private final AtomicBoolean requestOutstanding = new AtomicBoolean();
   private final MonoSink<Part> sink;
   private final int maxInMemorySize;
   private final long maxDiskUsagePerPart;
   private final Mono<Path> fileStorageDirectory;
   private final Scheduler blockingOperationScheduler;

   private PartGenerator(
      MonoSink<Part> sink, int maxInMemorySize, long maxDiskUsagePerPart, Mono<Path> fileStorageDirectory, Scheduler blockingOperationScheduler
   ) {
      this.sink = sink;
      this.maxInMemorySize = maxInMemorySize;
      this.maxDiskUsagePerPart = maxDiskUsagePerPart;
      this.fileStorageDirectory = fileStorageDirectory;
      this.blockingOperationScheduler = blockingOperationScheduler;
   }

   public static Mono<Part> createPart(
      Flux<MultipartParser.Token> tokens, int maxInMemorySize, long maxDiskUsagePerPart, Mono<Path> fileStorageDirectory, Scheduler blockingOperationScheduler
   ) {
      return Mono.create(sink -> {
         PartGenerator generator = new PartGenerator(sink, maxInMemorySize, maxDiskUsagePerPart, fileStorageDirectory, blockingOperationScheduler);
         sink.onCancel(generator);
         sink.onRequest(l -> generator.requestToken());
         tokens.doOnDiscard(MultipartParser.BodyToken.class, bodyToken -> DataBufferUtils.release(bodyToken.buffer())).subscribe(generator);
      });
   }

   public Context currentContext() {
      return Context.of(this.sink.contextView());
   }

   protected void hookOnSubscribe(Subscription subscription) {
      this.requestToken();
   }

   protected void hookOnNext(MultipartParser.Token token) {
      this.requestOutstanding.set(false);
      PartGenerator.State state = this.state.get();
      if (token instanceof MultipartParser.HeadersToken) {
         this.newPart(state, token.headers());
      } else {
         state.body(token.buffer());
      }
   }

   private void newPart(PartGenerator.State currentState, HttpHeaders headers) {
      if (MultipartUtils.isFormField(headers)) {
         this.changeState(currentState, new PartGenerator.FormFieldState(headers));
         this.requestToken();
      } else {
         this.changeState(currentState, new PartGenerator.InMemoryState(headers));
         this.requestToken();
      }
   }

   protected void hookOnComplete() {
      this.state.get().onComplete();
   }

   protected void hookOnError(Throwable throwable) {
      this.state.get().error(throwable);
      this.changeStateInternal(PartGenerator.DisposedState.INSTANCE);
      this.sink.error(throwable);
   }

   public void dispose() {
      this.changeStateInternal(PartGenerator.DisposedState.INSTANCE);
      this.cancel();
   }

   boolean changeState(PartGenerator.State oldState, PartGenerator.State newState) {
      if (this.state.compareAndSet(oldState, newState)) {
         if (logger.isTraceEnabled()) {
            logger.trace("Changed state: " + oldState + " -> " + newState);
         }

         oldState.dispose();
         return true;
      } else {
         logger.warn("Could not switch from " + oldState + " to " + newState + "; current state:" + this.state.get());
         return false;
      }
   }

   private void changeStateInternal(PartGenerator.State newState) {
      if (this.state.get() != PartGenerator.DisposedState.INSTANCE) {
         PartGenerator.State oldState = this.state.getAndSet(newState);
         if (logger.isTraceEnabled()) {
            logger.trace("Changed state: " + oldState + " -> " + newState);
         }

         oldState.dispose();
      }
   }

   void emitPart(Part part) {
      if (logger.isTraceEnabled()) {
         logger.trace("Emitting: " + part);
      }

      this.sink.success(part);
   }

   void emitError(Throwable t) {
      this.cancel();
      this.sink.error(t);
   }

   void requestToken() {
      if (this.upstream() != null && this.state.get().canRequest() && this.requestOutstanding.compareAndSet(false, true)) {
         this.request(1L);
      }
   }

   private final class CreateFileState implements PartGenerator.State {
      private final HttpHeaders headers;
      private final Collection<DataBuffer> content;
      private final long byteCount;
      private volatile boolean completed;
      private volatile boolean releaseOnDispose = true;

      public CreateFileState(HttpHeaders headers, Collection<DataBuffer> content, long byteCount) {
         this.headers = headers;
         this.content = content;
         this.byteCount = byteCount;
      }

      @Override
      public void body(DataBuffer dataBuffer) {
         DataBufferUtils.release(dataBuffer);
         PartGenerator.this.emitError(new IllegalStateException("Body token not expected"));
      }

      @Override
      public void onComplete() {
         this.completed = true;
      }

      public void createFile() {
         PartGenerator.this.fileStorageDirectory
            .map(this::createFileState)
            .subscribeOn(PartGenerator.this.blockingOperationScheduler)
            .subscribe(this::fileCreated, PartGenerator.this::emitError);
      }

      private PartGenerator.WritingFileState createFileState(Path directory) {
         try {
            Path tempFile = Files.createTempFile(directory, null, ".multipart");
            if (PartGenerator.logger.isTraceEnabled()) {
               PartGenerator.logger.trace("Storing multipart data in file " + tempFile);
            }

            WritableByteChannel channel = Files.newByteChannel(tempFile, StandardOpenOption.WRITE);
            return PartGenerator.this.new WritingFileState(this, tempFile, channel);
         } catch (IOException ex) {
            throw new UncheckedIOException("Could not create temp file in " + directory, ex);
         }
      }

      private void fileCreated(PartGenerator.WritingFileState newState) {
         this.releaseOnDispose = false;
         if (PartGenerator.this.changeState(this, newState)) {
            newState.writeBuffers(this.content);
            if (this.completed) {
               newState.onComplete();
            }
         } else {
            MultipartUtils.closeChannel(newState.channel);
            MultipartUtils.deleteFile(newState.file);
            this.content.forEach(DataBufferUtils::release);
         }
      }

      @Override
      public boolean canRequest() {
         return false;
      }

      @Override
      public void dispose() {
         if (this.releaseOnDispose) {
            this.content.forEach(DataBufferUtils::release);
         }
      }

      @Override
      public String toString() {
         return "CREATE-FILE";
      }
   }

   private static final class DisposedState implements PartGenerator.State {
      public static final PartGenerator.DisposedState INSTANCE = new PartGenerator.DisposedState();

      @Override
      public void body(DataBuffer dataBuffer) {
         DataBufferUtils.release(dataBuffer);
      }

      @Override
      public void onComplete() {
      }

      @Override
      public String toString() {
         return "DISPOSED";
      }
   }

   private final class FormFieldState implements PartGenerator.State {
      private final FastByteArrayOutputStream value = new FastByteArrayOutputStream();
      private final HttpHeaders headers;

      public FormFieldState(HttpHeaders headers) {
         this.headers = headers;
      }

      @Override
      public void body(DataBuffer dataBuffer) {
         int size = this.value.size() + dataBuffer.readableByteCount();
         if (PartGenerator.this.maxInMemorySize != -1 && size >= PartGenerator.this.maxInMemorySize) {
            DataBufferUtils.release(dataBuffer);
            PartGenerator.this.emitError(
               new DataBufferLimitException("Form field value exceeded the memory usage limit of " + PartGenerator.this.maxInMemorySize + " bytes")
            );
         } else {
            this.store(dataBuffer);
            PartGenerator.this.requestToken();
         }
      }

      private void store(DataBuffer dataBuffer) {
         try {
            byte[] bytes = new byte[dataBuffer.readableByteCount()];
            dataBuffer.read(bytes);
            this.value.write(bytes);
         } catch (IOException ex) {
            PartGenerator.this.emitError(ex);
         } finally {
            DataBufferUtils.release(dataBuffer);
         }
      }

      @Override
      public void onComplete() {
         byte[] bytes = this.value.toByteArrayUnsafe();
         String value = new String(bytes, MultipartUtils.charset(this.headers));
         PartGenerator.this.emitPart(DefaultParts.formFieldPart(this.headers, value));
      }

      @Override
      public String toString() {
         return "FORM-FIELD";
      }
   }

   private final class IdleFileState implements PartGenerator.State {
      private final HttpHeaders headers;
      private final Path file;
      private final WritableByteChannel channel;
      private final AtomicLong byteCount;
      private volatile boolean closeOnDispose = true;
      private volatile boolean deleteOnDispose = true;

      public IdleFileState(PartGenerator.WritingFileState state) {
         this.headers = state.headers;
         this.file = state.file;
         this.channel = state.channel;
         this.byteCount = state.byteCount;
      }

      @Override
      public void body(DataBuffer dataBuffer) {
         long count = this.byteCount.addAndGet(dataBuffer.readableByteCount());
         if (PartGenerator.this.maxDiskUsagePerPart != -1L && count > PartGenerator.this.maxDiskUsagePerPart) {
            MultipartUtils.closeChannel(this.channel);
            MultipartUtils.deleteFile(this.file);
            DataBufferUtils.release(dataBuffer);
            PartGenerator.this.emitError(
               new DataBufferLimitException("Part exceeded the disk usage limit of " + PartGenerator.this.maxDiskUsagePerPart + " bytes")
            );
         } else {
            this.closeOnDispose = false;
            this.deleteOnDispose = false;
            PartGenerator.WritingFileState newState = PartGenerator.this.new WritingFileState(this);
            if (PartGenerator.this.changeState(this, newState)) {
               newState.writeBuffer(dataBuffer);
            } else {
               MultipartUtils.closeChannel(this.channel);
               MultipartUtils.deleteFile(this.file);
               DataBufferUtils.release(dataBuffer);
            }
         }
      }

      @Override
      public void onComplete() {
         MultipartUtils.closeChannel(this.channel);
         this.deleteOnDispose = false;
         PartGenerator.this.emitPart(DefaultParts.part(this.headers, this.file, PartGenerator.this.blockingOperationScheduler));
      }

      @Override
      public void dispose() {
         if (this.closeOnDispose) {
            MultipartUtils.closeChannel(this.channel);
         }

         if (this.deleteOnDispose) {
            MultipartUtils.deleteFile(this.file);
         }
      }

      @Override
      public String toString() {
         return "IDLE-FILE";
      }
   }

   private final class InMemoryState implements PartGenerator.State {
      private final AtomicLong byteCount = new AtomicLong();
      private final Queue<DataBuffer> content = new ConcurrentLinkedQueue<>();
      private final HttpHeaders headers;
      private volatile boolean releaseOnDispose = true;

      public InMemoryState(HttpHeaders headers) {
         this.headers = headers;
      }

      @Override
      public void body(DataBuffer dataBuffer) {
         long prevCount = this.byteCount.get();
         long count = this.byteCount.addAndGet(dataBuffer.readableByteCount());
         if (PartGenerator.this.maxInMemorySize == -1 || count <= PartGenerator.this.maxInMemorySize) {
            this.storeBuffer(dataBuffer);
         } else if (prevCount <= PartGenerator.this.maxInMemorySize) {
            this.switchToFile(dataBuffer, count);
         } else {
            DataBufferUtils.release(dataBuffer);
            PartGenerator.this.emitError(new IllegalStateException("Body token not expected"));
         }
      }

      private void storeBuffer(DataBuffer dataBuffer) {
         this.content.add(dataBuffer);
         PartGenerator.this.requestToken();
      }

      private void switchToFile(DataBuffer current, long byteCount) {
         if (PartGenerator.this.maxDiskUsagePerPart != -1L && byteCount > PartGenerator.this.maxDiskUsagePerPart) {
            DataBufferUtils.release(current);
            this.dispose();
            PartGenerator.this.emitError(
               new DataBufferLimitException("Part exceeded the disk usage limit of " + PartGenerator.this.maxDiskUsagePerPart + " bytes")
            );
         } else {
            List<DataBuffer> content = new ArrayList<>(this.content);
            content.add(current);
            this.releaseOnDispose = false;
            PartGenerator.CreateFileState newState = PartGenerator.this.new CreateFileState(this.headers, content, byteCount);
            if (PartGenerator.this.changeState(this, newState)) {
               newState.createFile();
            } else {
               content.forEach(DataBufferUtils::release);
            }
         }
      }

      @Override
      public void onComplete() {
         this.emitMemoryPart();
      }

      private void emitMemoryPart() {
         byte[] bytes = new byte[(int)this.byteCount.get()];
         int idx = 0;

         for (DataBuffer buffer : this.content) {
            int len = buffer.readableByteCount();
            buffer.read(bytes, idx, len);
            idx += len;
            DataBufferUtils.release(buffer);
         }

         this.content.clear();
         Flux<DataBuffer> content = Flux.just(DefaultDataBufferFactory.sharedInstance.wrap(bytes));
         PartGenerator.this.emitPart(DefaultParts.part(this.headers, content));
      }

      @Override
      public void dispose() {
         if (this.releaseOnDispose) {
            this.content.forEach(DataBufferUtils::release);
         }
      }

      @Override
      public String toString() {
         return "IN-MEMORY";
      }
   }

   private final class InitialState implements PartGenerator.State {
      @Override
      public void body(DataBuffer dataBuffer) {
         DataBufferUtils.release(dataBuffer);
         PartGenerator.this.emitError(new IllegalStateException("Body token not expected"));
      }

      @Override
      public void onComplete() {
      }

      @Override
      public String toString() {
         return "INITIAL";
      }
   }

   private interface State {
      void body(DataBuffer dataBuffer);

      void onComplete();

      default void error(Throwable throwable) {
      }

      default boolean canRequest() {
         return true;
      }

      default void dispose() {
      }
   }

   private final class WritingFileState implements PartGenerator.State {
      private final HttpHeaders headers;
      private final Path file;
      private final WritableByteChannel channel;
      private final AtomicLong byteCount;
      private volatile boolean completed;
      private volatile boolean disposed;

      public WritingFileState(PartGenerator.CreateFileState state, Path file, WritableByteChannel channel) {
         this.headers = state.headers;
         this.file = file;
         this.channel = channel;
         this.byteCount = new AtomicLong(state.byteCount);
      }

      public WritingFileState(PartGenerator.IdleFileState state) {
         this.headers = state.headers;
         this.file = state.file;
         this.channel = state.channel;
         this.byteCount = state.byteCount;
      }

      @Override
      public void body(DataBuffer dataBuffer) {
         DataBufferUtils.release(dataBuffer);
         PartGenerator.this.emitError(new IllegalStateException("Body token not expected"));
      }

      @Override
      public void onComplete() {
         this.completed = true;
         PartGenerator.State state = PartGenerator.this.state.get();
         if (state != this) {
            state.onComplete();
         } else {
            this.completed = true;
         }
      }

      public void writeBuffer(DataBuffer dataBuffer) {
         Mono.just(dataBuffer)
            .flatMap(this::writeInternal)
            .subscribeOn(PartGenerator.this.blockingOperationScheduler)
            .subscribe(null, PartGenerator.this::emitError, this::writeComplete);
      }

      public void writeBuffers(Iterable<DataBuffer> dataBuffers) {
         Flux.fromIterable(dataBuffers)
            .concatMap(this::writeInternal)
            .then()
            .subscribeOn(PartGenerator.this.blockingOperationScheduler)
            .subscribe(null, PartGenerator.this::emitError, this::writeComplete);
      }

      private void writeComplete() {
         PartGenerator.IdleFileState newState = PartGenerator.this.new IdleFileState(this);
         if (this.disposed) {
            newState.dispose();
         } else if (PartGenerator.this.changeState(this, newState)) {
            if (this.completed) {
               newState.onComplete();
            } else {
               PartGenerator.this.requestToken();
            }
         } else {
            MultipartUtils.closeChannel(this.channel);
            MultipartUtils.deleteFile(this.file);
         }
      }

      private Mono<Void> writeInternal(DataBuffer dataBuffer) {
         try {
            ByteBufferIterator iterator = dataBuffer.readableByteBuffers();

            try {
               while (iterator.hasNext()) {
                  ByteBuffer byteBuffer = (ByteBuffer)iterator.next();

                  while (byteBuffer.hasRemaining()) {
                     this.channel.write(byteBuffer);
                  }
               }
            } catch (Throwable var11) {
               if (iterator != null) {
                  try {
                     iterator.close();
                  } catch (Throwable var10) {
                     var11.addSuppressed(var10);
                  }
               }

               throw var11;
            }

            if (iterator != null) {
               iterator.close();
            }

            return Mono.empty();
         } catch (IOException ex) {
            MultipartUtils.closeChannel(this.channel);
            MultipartUtils.deleteFile(this.file);
            return Mono.error(ex);
         } finally {
            DataBufferUtils.release(dataBuffer);
         }
      }

      @Override
      public boolean canRequest() {
         return false;
      }

      @Override
      public void dispose() {
         this.disposed = true;
      }

      @Override
      public String toString() {
         return "WRITE-FILE";
      }
   }
}
