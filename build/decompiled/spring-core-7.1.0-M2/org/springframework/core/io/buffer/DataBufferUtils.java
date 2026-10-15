package org.springframework.core.io.buffer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.Channel;
import java.nio.channels.Channels;
import java.nio.channels.CompletionHandler;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscription;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import reactor.core.Exceptions;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SynchronousSink;
import reactor.util.context.Context;

public abstract class DataBufferUtils {
   private static final Log logger = LogFactory.getLog(DataBufferUtils.class);
   private static final Consumer<DataBuffer> RELEASE_CONSUMER = DataBufferUtils::release;

   public static Flux<DataBuffer> readInputStream(Callable<InputStream> inputStreamSupplier, DataBufferFactory bufferFactory, int bufferSize) {
      Assert.notNull(inputStreamSupplier, "'inputStreamSupplier' must not be null");
      return readByteChannel(() -> Channels.newChannel(inputStreamSupplier.call()), bufferFactory, bufferSize);
   }

   public static Flux<DataBuffer> readByteChannel(Callable<ReadableByteChannel> channelSupplier, DataBufferFactory bufferFactory, int bufferSize) {
      Assert.notNull(channelSupplier, "'channelSupplier' must not be null");
      Assert.notNull(bufferFactory, "'bufferFactory' must not be null");
      Assert.isTrue(bufferSize > 0, "'bufferSize' must be > 0");
      return Flux.using(
         channelSupplier,
         channel -> Flux.generate(new DataBufferUtils.ReadableByteChannelGenerator(channel, bufferFactory, bufferSize)),
         DataBufferUtils::closeChannel
      );
   }

   public static Flux<DataBuffer> readAsynchronousFileChannel(
      Callable<AsynchronousFileChannel> channelSupplier, DataBufferFactory bufferFactory, int bufferSize
   ) {
      return readAsynchronousFileChannel(channelSupplier, 0L, bufferFactory, bufferSize);
   }

   public static Flux<DataBuffer> readAsynchronousFileChannel(
      Callable<AsynchronousFileChannel> channelSupplier, long position, DataBufferFactory bufferFactory, int bufferSize
   ) {
      Assert.notNull(channelSupplier, "'channelSupplier' must not be null");
      Assert.notNull(bufferFactory, "'bufferFactory' must not be null");
      Assert.isTrue(position >= 0L, "'position' must be >= 0");
      Assert.isTrue(bufferSize > 0, "'bufferSize' must be > 0");
      Flux<DataBuffer> flux = Flux.using(channelSupplier, channel -> Flux.create(sink -> {
         DataBufferUtils.ReadCompletionHandler handler = new DataBufferUtils.ReadCompletionHandler(channel, sink, position, bufferFactory, bufferSize);
         sink.onCancel(handler::cancel);
         sink.onRequest(handler::request);
      }), channel -> {});
      return flux.doOnDiscard(DataBuffer.class, DataBufferUtils::release);
   }

   public static Flux<DataBuffer> read(Path path, DataBufferFactory bufferFactory, int bufferSize, OpenOption... options) {
      Assert.notNull(path, "Path must not be null");
      Assert.notNull(bufferFactory, "DataBufferFactory must not be null");
      Assert.isTrue(bufferSize > 0, "'bufferSize' must be > 0");
      if (options.length > 0) {
         for (OpenOption option : options) {
            Assert.isTrue(option != StandardOpenOption.APPEND && option != StandardOpenOption.WRITE, () -> "'" + option + "' not allowed");
         }
      }

      return readAsynchronousFileChannel(() -> AsynchronousFileChannel.open(path, options), bufferFactory, bufferSize);
   }

   public static Flux<DataBuffer> read(Resource resource, DataBufferFactory bufferFactory, int bufferSize) {
      return read(resource, 0L, bufferFactory, bufferSize);
   }

   public static Flux<DataBuffer> read(Resource resource, long position, DataBufferFactory bufferFactory, int bufferSize) {
      try {
         if (resource.isFile()) {
            Path filePath = resource.getFilePath();
            return readAsynchronousFileChannel(() -> AsynchronousFileChannel.open(filePath, StandardOpenOption.READ), position, bufferFactory, bufferSize);
         }
      } catch (IOException var6) {
      }

      Flux<DataBuffer> result = readByteChannel(resource::readableChannel, bufferFactory, bufferSize);
      return position == 0L ? result : skipUntilByteCount(result, position);
   }

   public static Flux<DataBuffer> write(Publisher<DataBuffer> source, OutputStream outputStream) {
      Assert.notNull(source, "'source' must not be null");
      Assert.notNull(outputStream, "'outputStream' must not be null");
      WritableByteChannel channel = Channels.newChannel(outputStream);
      return write(source, channel);
   }

   public static Flux<DataBuffer> write(Publisher<DataBuffer> source, WritableByteChannel channel) {
      Assert.notNull(source, "'source' must not be null");
      Assert.notNull(channel, "'channel' must not be null");
      Flux<DataBuffer> flux = Flux.from(source);
      return Flux.create(sink -> {
         DataBufferUtils.WritableByteChannelSubscriber subscriber = new DataBufferUtils.WritableByteChannelSubscriber(sink, channel);
         sink.onDispose(subscriber);
         flux.subscribe(subscriber);
      });
   }

   public static Flux<DataBuffer> write(Publisher<DataBuffer> source, AsynchronousFileChannel channel) {
      return write(source, channel, 0L);
   }

   public static Flux<DataBuffer> write(Publisher<? extends DataBuffer> source, AsynchronousFileChannel channel, long position) {
      Assert.notNull(source, "'source' must not be null");
      Assert.notNull(channel, "'channel' must not be null");
      Assert.isTrue(position >= 0L, "'position' must be >= 0");
      Flux<DataBuffer> flux = Flux.from(source);
      return Flux.create(sink -> {
         DataBufferUtils.WriteCompletionHandler handler = new DataBufferUtils.WriteCompletionHandler(sink, channel, position);
         sink.onDispose(handler);
         flux.subscribe(handler);
      });
   }

   public static Mono<Void> write(Publisher<DataBuffer> source, Path destination, OpenOption... options) {
      Assert.notNull(source, "Source must not be null");
      Assert.notNull(destination, "Destination must not be null");
      Set<OpenOption> optionSet = checkWriteOptions(options);
      return Mono.create(sink -> {
         try {
            AsynchronousFileChannel channel = AsynchronousFileChannel.open(destination, optionSet, null);
            sink.onDispose(() -> closeChannel(channel));
            write(source, channel).subscribe(DataBufferUtils::release, sink::error, sink::success, Context.of(sink.contextView()));
         } catch (IOException ex) {
            sink.error(ex);
         }
      });
   }

   private static Set<OpenOption> checkWriteOptions(OpenOption[] options) {
      int length = options.length;
      Set<OpenOption> result = CollectionUtils.newHashSet(length > 0 ? length : 2);
      if (length == 0) {
         result.add(StandardOpenOption.CREATE);
         result.add(StandardOpenOption.TRUNCATE_EXISTING);
      } else {
         for (OpenOption opt : options) {
            if (opt == StandardOpenOption.READ) {
               throw new IllegalArgumentException("READ not allowed");
            }

            result.add(opt);
         }
      }

      result.add(StandardOpenOption.WRITE);
      return result;
   }

   static void closeChannel(@Nullable Channel channel) {
      if (channel != null && channel.isOpen()) {
         try {
            channel.close();
         } catch (IOException var2) {
         }
      }
   }

   public static Publisher<DataBuffer> outputStreamPublisher(Consumer<OutputStream> consumer, DataBufferFactory bufferFactory, Executor executor) {
      return new OutputStreamPublisher<>(consumer::accept, new DataBufferUtils.DataBufferMapper(bufferFactory), executor, null);
   }

   public static Publisher<DataBuffer> outputStreamPublisher(Consumer<OutputStream> consumer, DataBufferFactory bufferFactory, Executor executor, int chunkSize) {
      return new OutputStreamPublisher<>(consumer::accept, new DataBufferUtils.DataBufferMapper(bufferFactory), executor, chunkSize);
   }

   public static <T extends DataBuffer> InputStream subscriberInputStream(Publisher<T> publisher, int demand) {
      Assert.notNull(publisher, "Publisher must not be null");
      Assert.isTrue(demand > 0, "maxBufferCount must be > 0");
      SubscriberInputStream subscriber = new SubscriberInputStream(demand);
      publisher.subscribe(subscriber);
      return subscriber;
   }

   public static <T extends DataBuffer> Flux<T> takeUntilByteCount(Publisher<T> publisher, long maxByteCount) {
      Assert.notNull(publisher, "Publisher must not be null");
      Assert.isTrue(maxByteCount >= 0L, "'maxByteCount' must be >= 0");
      return Flux.defer(() -> {
         AtomicLong countDown = new AtomicLong(maxByteCount);
         return Flux.from(publisher).map(buffer -> {
            long remainder = countDown.addAndGet(-buffer.readableByteCount());
            if (remainder < 0L) {
               int index = buffer.readableByteCount() + (int)remainder;
               DataBuffer split = buffer.split(index);
               release(buffer);
               return split;
            } else {
               return buffer;
            }
         }).takeUntil(buffer -> countDown.get() <= 0L);
      });
   }

   public static <T extends DataBuffer> Flux<T> skipUntilByteCount(Publisher<T> publisher, long maxByteCount) {
      Assert.notNull(publisher, "Publisher must not be null");
      Assert.isTrue(maxByteCount >= 0L, "'maxByteCount' must be >= 0");
      return Flux.defer(() -> {
         AtomicLong countDown = new AtomicLong(maxByteCount);
         return Flux.from(publisher).skipUntil(buffer -> {
            long remainder = countDown.addAndGet(-buffer.readableByteCount());
            return remainder < 0L;
         }).map(buffer -> {
            long remainder = countDown.get();
            if (remainder < 0L) {
               countDown.set(0L);
               int start = buffer.readableByteCount() + (int)remainder;
               DataBuffer split = buffer.split(start);
               release(split);
               return buffer;
            } else {
               return buffer;
            }
         });
      }).doOnDiscard(DataBuffer.class, DataBufferUtils::release);
   }

   public static <T extends DataBuffer> T retain(T dataBuffer) {
      return (T)(dataBuffer instanceof PooledDataBuffer pooledDataBuffer ? pooledDataBuffer.retain() : dataBuffer);
   }

   public static <T extends DataBuffer> T touch(T dataBuffer, Object hint) {
      return (T)(dataBuffer instanceof TouchableDataBuffer touchableDataBuffer ? touchableDataBuffer.touch(hint) : dataBuffer);
   }

   public static boolean release(@Nullable DataBuffer dataBuffer) {
      if (dataBuffer instanceof PooledDataBuffer pooledDataBuffer) {
         if (pooledDataBuffer.isAllocated()) {
            try {
               return pooledDataBuffer.release();
            } catch (IllegalStateException ex) {
               if (logger.isDebugEnabled()) {
                  logger.debug("Failed to release PooledDataBuffer: " + dataBuffer, ex);
               }

               return false;
            }
         }
      } else if (dataBuffer instanceof CloseableDataBuffer closeableDataBuffer) {
         try {
            closeableDataBuffer.close();
            return true;
         } catch (IllegalStateException ex) {
            if (logger.isDebugEnabled()) {
               logger.debug("Failed to release CloseableDataBuffer " + dataBuffer, ex);
            }

            return false;
         }
      }

      return false;
   }

   public static Consumer<DataBuffer> releaseConsumer() {
      return RELEASE_CONSUMER;
   }

   public static Mono<DataBuffer> join(Publisher<? extends DataBuffer> dataBuffers) {
      return join(dataBuffers, -1);
   }

   public static Mono<DataBuffer> join(Publisher<? extends DataBuffer> buffers, int maxByteCount) {
      Assert.notNull(buffers, "'buffers' must not be null");
      return buffers instanceof Mono mono
         ? mono
         : Flux.from(buffers)
            .collect(() -> new LimitedDataBufferList(maxByteCount), LimitedDataBufferList::add)
            .filter(list -> !list.isEmpty())
            .map(list -> list.get(0).factory().join(list))
            .doOnDiscard(DataBuffer.class, DataBufferUtils::release);
   }

   public static DataBufferUtils.Matcher matcher(byte[] delimiter) {
      return createMatcher(delimiter);
   }

   public static DataBufferUtils.Matcher matcher(byte[]... delimiters) {
      Assert.isTrue(delimiters.length > 0, "Delimiters must not be empty");
      return delimiters.length == 1 ? createMatcher(delimiters[0]) : new DataBufferUtils.CompositeMatcher(delimiters);
   }

   private static DataBufferUtils.NestedMatcher createMatcher(byte[] delimiter) {
      int length = delimiter.length;
      Assert.isTrue(length > 0, "Delimiter must not be empty");

      return switch (length) {
         case 1 -> delimiter[0] == 10 ? DataBufferUtils.SingleByteMatcher.NEWLINE_MATCHER : new DataBufferUtils.SingleByteMatcher(delimiter);
         case 2 -> new DataBufferUtils.TwoByteMatcher(delimiter);
         default -> new DataBufferUtils.KnuthMorrisPrattMatcher(delimiter);
      };
   }

   private abstract static class AbstractNestedMatcher implements DataBufferUtils.NestedMatcher {
      private final byte[] delimiter;
      private int matches = 0;

      protected AbstractNestedMatcher(byte[] delimiter) {
         this.delimiter = delimiter;
      }

      protected void setMatches(int index) {
         this.matches = index;
      }

      protected int getMatches() {
         return this.matches;
      }

      @Override
      public int match(DataBuffer dataBuffer) {
         int start = dataBuffer.readPosition();
         int end = dataBuffer.writePosition();
         int matchPosition = dataBuffer.forEachByte(start, end - start, b -> !this.match(b));
         if (matchPosition != -1) {
            this.reset();
         }

         return matchPosition;
      }

      @Override
      public boolean match(byte b) {
         if (b == this.delimiter[this.matches]) {
            this.matches++;
            return this.matches == this.delimiter().length;
         } else {
            return false;
         }
      }

      @Override
      public byte[] delimiter() {
         return this.delimiter;
      }

      @Override
      public void reset() {
         this.matches = 0;
      }
   }

   private static class CompositeMatcher implements DataBufferUtils.Matcher {
      private static final byte[] NO_DELIMITER = new byte[0];
      private final DataBufferUtils.NestedMatcher[] matchers;
      byte[] longestDelimiter = NO_DELIMITER;

      CompositeMatcher(byte[][] delimiters) {
         this.matchers = initMatchers(delimiters);
      }

      private static DataBufferUtils.NestedMatcher[] initMatchers(byte[][] delimiters) {
         DataBufferUtils.NestedMatcher[] matchers = new DataBufferUtils.NestedMatcher[delimiters.length];

         for (int i = 0; i < delimiters.length; i++) {
            matchers[i] = DataBufferUtils.createMatcher(delimiters[i]);
         }

         return matchers;
      }

      @Override
      public int match(DataBuffer dataBuffer) {
         this.longestDelimiter = NO_DELIMITER;

         for (int pos = dataBuffer.readPosition(); pos < dataBuffer.writePosition(); pos++) {
            byte b = dataBuffer.getByte(pos);

            for (DataBufferUtils.NestedMatcher matcher : this.matchers) {
               if (matcher.match(b) && matcher.delimiter().length > this.longestDelimiter.length) {
                  this.longestDelimiter = matcher.delimiter();
               }
            }

            if (this.longestDelimiter != NO_DELIMITER) {
               this.reset();
               return pos;
            }
         }

         return -1;
      }

      @Override
      public byte[] delimiter() {
         Assert.state(this.longestDelimiter != NO_DELIMITER, "'delimiter' not set");
         return this.longestDelimiter;
      }

      @Override
      public void reset() {
         for (DataBufferUtils.NestedMatcher matcher : this.matchers) {
            matcher.reset();
         }
      }
   }

   private static final class DataBufferMapper implements OutputStreamPublisher.ByteMapper<DataBuffer> {
      private final DataBufferFactory bufferFactory;

      private DataBufferMapper(DataBufferFactory bufferFactory) {
         this.bufferFactory = bufferFactory;
      }

      public DataBuffer map(int b) {
         DataBuffer buffer = this.bufferFactory.allocateBuffer(1);
         buffer.write((byte)b);
         return buffer;
      }

      public DataBuffer map(byte[] b, int off, int len) {
         DataBuffer buffer = this.bufferFactory.allocateBuffer(len);
         buffer.write(b, off, len);
         return buffer;
      }
   }

   private static class KnuthMorrisPrattMatcher extends DataBufferUtils.AbstractNestedMatcher {
      private final int[] table;

      public KnuthMorrisPrattMatcher(byte[] delimiter) {
         super(delimiter);
         this.table = longestSuffixPrefixTable(delimiter);
      }

      private static int[] longestSuffixPrefixTable(byte[] delimiter) {
         int[] result = new int[delimiter.length];
         result[0] = 0;

         for (int i = 1; i < delimiter.length; i++) {
            int j = result[i - 1];

            while (j > 0 && delimiter[i] != delimiter[j]) {
               j = result[j - 1];
            }

            if (delimiter[i] == delimiter[j]) {
               j++;
            }

            result[i] = j;
         }

         return result;
      }

      @Override
      public boolean match(byte b) {
         while (this.getMatches() > 0 && b != this.delimiter()[this.getMatches()]) {
            this.setMatches(this.table[this.getMatches() - 1]);
         }

         return super.match(b);
      }
   }

   public interface Matcher {
      int match(DataBuffer dataBuffer);

      byte[] delimiter();

      void reset();
   }

   private interface NestedMatcher extends DataBufferUtils.Matcher {
      boolean match(byte b);
   }

   private static class ReadCompletionHandler implements CompletionHandler<Integer, DataBufferUtils.ReadCompletionHandler.Attachment> {
      private final AsynchronousFileChannel channel;
      private final FluxSink<DataBuffer> sink;
      private final DataBufferFactory dataBufferFactory;
      private final int bufferSize;
      private final AtomicLong position;
      private final AtomicReference<DataBufferUtils.ReadCompletionHandler.State> state = new AtomicReference<>(DataBufferUtils.ReadCompletionHandler.State.IDLE);

      public ReadCompletionHandler(
         AsynchronousFileChannel channel, FluxSink<DataBuffer> sink, long position, DataBufferFactory dataBufferFactory, int bufferSize
      ) {
         this.channel = channel;
         this.sink = sink;
         this.position = new AtomicLong(position);
         this.dataBufferFactory = dataBufferFactory;
         this.bufferSize = bufferSize;
      }

      public void request(long n) {
         this.tryRead();
      }

      public void cancel() {
         this.state.getAndSet(DataBufferUtils.ReadCompletionHandler.State.DISPOSED);
         DataBufferUtils.closeChannel(this.channel);
      }

      private void tryRead() {
         if (this.sink.requestedFromDownstream() > 0L
            && this.state.compareAndSet(DataBufferUtils.ReadCompletionHandler.State.IDLE, DataBufferUtils.ReadCompletionHandler.State.READING)) {
            this.read();
         }
      }

      private void read() {
         DataBuffer dataBuffer = this.dataBufferFactory.allocateBuffer(this.bufferSize);
         DataBuffer.ByteBufferIterator iterator = dataBuffer.writableByteBuffers();
         Assert.state(iterator.hasNext(), "No ByteBuffer available");
         ByteBuffer byteBuffer = iterator.next();
         DataBufferUtils.ReadCompletionHandler.Attachment attachment = new DataBufferUtils.ReadCompletionHandler.Attachment(dataBuffer, iterator);

         try {
            this.channel.read(byteBuffer, this.position.get(), attachment, this);
         } catch (Throwable ex) {
            Exceptions.throwIfFatal(ex);
            this.failed(ex, attachment);
         }
      }

      public void completed(Integer read, DataBufferUtils.ReadCompletionHandler.Attachment attachment) {
         attachment.iterator().close();
         DataBuffer dataBuffer = attachment.dataBuffer();
         if (this.state.get() == DataBufferUtils.ReadCompletionHandler.State.DISPOSED) {
            DataBufferUtils.release(dataBuffer);
            DataBufferUtils.closeChannel(this.channel);
         } else if (read == -1) {
            DataBufferUtils.release(dataBuffer);
            DataBufferUtils.closeChannel(this.channel);
            this.state.set(DataBufferUtils.ReadCompletionHandler.State.DISPOSED);
            this.sink.complete();
         } else {
            this.position.addAndGet(read.intValue());
            dataBuffer.writePosition(read);
            this.sink.next(dataBuffer);
            if (this.sink.requestedFromDownstream() > 0L) {
               this.read();
            } else {
               if (this.state.compareAndSet(DataBufferUtils.ReadCompletionHandler.State.READING, DataBufferUtils.ReadCompletionHandler.State.IDLE)) {
                  this.tryRead();
               }
            }
         }
      }

      public void failed(Throwable ex, DataBufferUtils.ReadCompletionHandler.Attachment attachment) {
         attachment.iterator().close();
         DataBufferUtils.release(attachment.dataBuffer());
         DataBufferUtils.closeChannel(this.channel);
         this.state.set(DataBufferUtils.ReadCompletionHandler.State.DISPOSED);
         this.sink.error(ex);
      }

      private record Attachment(DataBuffer dataBuffer, DataBuffer.ByteBufferIterator iterator) {
      }

      private enum State {
         IDLE,
         READING,
         DISPOSED;
      }
   }

   private static class ReadableByteChannelGenerator implements Consumer<SynchronousSink<DataBuffer>> {
      private final ReadableByteChannel channel;
      private final DataBufferFactory dataBufferFactory;
      private final int bufferSize;

      public ReadableByteChannelGenerator(ReadableByteChannel channel, DataBufferFactory dataBufferFactory, int bufferSize) {
         this.channel = channel;
         this.dataBufferFactory = dataBufferFactory;
         this.bufferSize = bufferSize;
      }

      public void accept(SynchronousSink<DataBuffer> sink) {
         int read = -1;
         DataBuffer dataBuffer = this.dataBufferFactory.allocateBuffer(this.bufferSize);

         try {
            try (DataBuffer.ByteBufferIterator iterator = dataBuffer.writableByteBuffers()) {
               Assert.state(iterator.hasNext(), "No ByteBuffer available");
               ByteBuffer byteBuffer = iterator.next();
               read = this.channel.read(byteBuffer);
            }

            if (read >= 0) {
               dataBuffer.writePosition(read);
               sink.next(dataBuffer);
            } else {
               sink.complete();
            }
         } catch (IOException ex) {
            sink.error(ex);
         } finally {
            if (read == -1) {
               DataBufferUtils.release(dataBuffer);
            }
         }
      }
   }

   private static class SingleByteMatcher implements DataBufferUtils.NestedMatcher {
      static final DataBufferUtils.SingleByteMatcher NEWLINE_MATCHER = new DataBufferUtils.SingleByteMatcher(new byte[]{10});
      private final byte[] delimiter;

      SingleByteMatcher(byte[] delimiter) {
         Assert.isTrue(delimiter.length == 1, "Expected a 1 byte delimiter");
         this.delimiter = delimiter;
      }

      @Override
      public int match(DataBuffer dataBuffer) {
         int start = dataBuffer.readPosition();
         int end = dataBuffer.writePosition();
         return dataBuffer.forEachByte(start, end - start, b -> !this.match(b));
      }

      @Override
      public boolean match(byte b) {
         return this.delimiter[0] == b;
      }

      @Override
      public byte[] delimiter() {
         return this.delimiter;
      }

      @Override
      public void reset() {
      }
   }

   private static class TwoByteMatcher extends DataBufferUtils.AbstractNestedMatcher {
      protected TwoByteMatcher(byte[] delimiter) {
         super(delimiter);
         Assert.isTrue(delimiter.length == 2, "Expected a 2-byte delimiter");
      }

      @Override
      public boolean match(byte b) {
         if (this.getMatches() > 0 && b != this.delimiter()[this.getMatches()]) {
            this.setMatches(0);
         }

         return super.match(b);
      }
   }

   private static class WritableByteChannelSubscriber extends BaseSubscriber<DataBuffer> {
      private final FluxSink<DataBuffer> sink;
      private final WritableByteChannel channel;

      public WritableByteChannelSubscriber(FluxSink<DataBuffer> sink, WritableByteChannel channel) {
         this.sink = sink;
         this.channel = channel;
      }

      protected void hookOnSubscribe(Subscription subscription) {
         this.request(1L);
      }

      protected void hookOnNext(DataBuffer dataBuffer) {
         try {
            try (DataBuffer.ByteBufferIterator iterator = dataBuffer.readableByteBuffers()) {
               while (iterator.hasNext()) {
                  ByteBuffer byteBuffer = iterator.next();

                  while (byteBuffer.hasRemaining()) {
                     this.channel.write(byteBuffer);
                  }
               }
            }

            this.sink.next(dataBuffer);
            this.request(1L);
         } catch (IOException ex) {
            this.sink.next(dataBuffer);
            this.sink.error(ex);
         }
      }

      protected void hookOnError(Throwable throwable) {
         this.sink.error(throwable);
      }

      protected void hookOnComplete() {
         this.sink.complete();
      }

      public Context currentContext() {
         return Context.of(this.sink.contextView());
      }
   }

   private static class WriteCompletionHandler
      extends BaseSubscriber<DataBuffer>
      implements CompletionHandler<Integer, DataBufferUtils.WriteCompletionHandler.Attachment> {
      private final FluxSink<DataBuffer> sink;
      private final AsynchronousFileChannel channel;
      private final AtomicBoolean writing = new AtomicBoolean();
      private final AtomicBoolean completed = new AtomicBoolean();
      private final AtomicReference<Throwable> error = new AtomicReference<>();
      private final AtomicLong position;

      public WriteCompletionHandler(FluxSink<DataBuffer> sink, AsynchronousFileChannel channel, long position) {
         this.sink = sink;
         this.channel = channel;
         this.position = new AtomicLong(position);
      }

      protected void hookOnSubscribe(Subscription subscription) {
         this.request(1L);
      }

      protected void hookOnNext(DataBuffer dataBuffer) {
         DataBuffer.ByteBufferIterator iterator = dataBuffer.readableByteBuffers();
         if (iterator.hasNext()) {
            ByteBuffer byteBuffer = iterator.next();
            long pos = this.position.get();
            DataBufferUtils.WriteCompletionHandler.Attachment attachment = new DataBufferUtils.WriteCompletionHandler.Attachment(
               byteBuffer, dataBuffer, iterator
            );
            this.writing.set(true);
            this.write(byteBuffer, pos, attachment);
         } else {
            iterator.close();
            this.sink.next(dataBuffer);
            this.request(1L);
         }
      }

      protected void hookOnError(Throwable throwable) {
         this.error.set(throwable);
         if (!this.writing.get()) {
            this.sink.error(throwable);
         }
      }

      protected void hookOnComplete() {
         this.completed.set(true);
         if (!this.writing.get()) {
            this.sink.complete();
         }
      }

      public void completed(Integer written, DataBufferUtils.WriteCompletionHandler.Attachment attachment) {
         DataBuffer.ByteBufferIterator iterator = attachment.iterator();
         long pos = this.position.addAndGet(written.intValue());
         ByteBuffer byteBuffer = attachment.byteBuffer();
         if (byteBuffer.hasRemaining()) {
            this.write(byteBuffer, pos, attachment);
         } else if (iterator.hasNext()) {
            ByteBuffer next = iterator.next();
            DataBufferUtils.WriteCompletionHandler.Attachment nextAttachment = new DataBufferUtils.WriteCompletionHandler.Attachment(
               next, attachment.dataBuffer(), iterator
            );
            this.write(next, pos, nextAttachment);
         } else {
            iterator.close();
            this.sink.next(attachment.dataBuffer());
            this.writing.set(false);
            Throwable throwable = this.error.get();
            if (throwable != null) {
               this.sink.error(throwable);
            } else if (this.completed.get()) {
               this.sink.complete();
            } else {
               this.request(1L);
            }
         }
      }

      private void write(ByteBuffer byteBuffer, long pos, DataBufferUtils.WriteCompletionHandler.Attachment attachment) {
         try {
            this.channel.write(byteBuffer, pos, attachment, this);
         } catch (Throwable ex) {
            Exceptions.throwIfFatal(ex);
            this.failed(ex, attachment);
         }
      }

      public void failed(Throwable ex, DataBufferUtils.WriteCompletionHandler.Attachment attachment) {
         attachment.iterator().close();
         this.sink.next(attachment.dataBuffer());
         this.writing.set(false);
         this.sink.error(ex);
      }

      public Context currentContext() {
         return Context.of(this.sink.contextView());
      }

      private record Attachment(ByteBuffer byteBuffer, DataBuffer dataBuffer, DataBuffer.ByteBufferIterator iterator) {
      }
   }
}
