package org.springframework.http.codec.multipart;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Subscription;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DataBufferUtils.Matcher;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.util.context.Context;

final class MultipartParser extends BaseSubscriber<DataBuffer> {
   private static final byte CR = 13;
   private static final byte LF = 10;
   private static final byte[] CR_LF = new byte[]{13, 10};
   private static final byte HYPHEN = 45;
   private static final byte[] TWO_HYPHENS = new byte[]{45, 45};
   private static final String HEADER_ENTRY_SEPARATOR = "\\r\\n";
   private static final Log logger = LogFactory.getLog(MultipartParser.class);
   private final AtomicReference<MultipartParser.State> state;
   private final FluxSink<MultipartParser.Token> sink;
   private final byte[] boundary;
   private final int maxHeadersSize;
   private final AtomicBoolean requestOutstanding = new AtomicBoolean();
   private final Charset headersCharset;

   private MultipartParser(FluxSink<MultipartParser.Token> sink, byte[] boundary, int maxHeadersSize, Charset headersCharset) {
      this.sink = sink;
      this.boundary = boundary;
      this.maxHeadersSize = maxHeadersSize;
      this.headersCharset = headersCharset;
      this.state = new AtomicReference<>(new MultipartParser.PreambleState());
   }

   public static Flux<MultipartParser.Token> parse(Flux<DataBuffer> buffers, byte[] boundary, int maxHeadersSize, Charset headersCharset) {
      return Flux.create(sink -> {
         MultipartParser parser = new MultipartParser(sink, boundary, maxHeadersSize, headersCharset);
         sink.onCancel(parser::onSinkCancel);
         sink.onRequest(l -> parser.requestBuffer());
         buffers.subscribe(parser);
      }).doOnDiscard(MultipartParser.BodyToken.class, body -> DataBufferUtils.release(body.buffer()));
   }

   public Context currentContext() {
      return Context.of(this.sink.contextView());
   }

   protected void hookOnSubscribe(Subscription subscription) {
      if (this.sink.requestedFromDownstream() > 0L) {
         this.requestBuffer();
      }
   }

   protected void hookOnNext(DataBuffer value) {
      this.requestOutstanding.set(false);
      this.state.get().onNext(value);
   }

   protected void hookOnComplete() {
      this.state.get().onComplete();
   }

   protected void hookOnError(Throwable throwable) {
      MultipartParser.State oldState = this.state.getAndSet(MultipartParser.DisposedState.INSTANCE);
      oldState.dispose();
      this.sink.error(throwable);
   }

   private void onSinkCancel() {
      MultipartParser.State oldState = this.state.getAndSet(MultipartParser.DisposedState.INSTANCE);
      oldState.dispose();
      this.cancel();
   }

   boolean changeState(MultipartParser.State oldState, MultipartParser.State newState, @Nullable DataBuffer remainder) {
      if (this.state.compareAndSet(oldState, newState)) {
         if (logger.isTraceEnabled()) {
            logger.trace("Changed state: " + oldState + " -> " + newState);
         }

         oldState.dispose();
         if (remainder != null) {
            if (remainder.readableByteCount() > 0) {
               newState.onNext(remainder);
            } else {
               DataBufferUtils.release(remainder);
               this.requestBuffer();
            }
         }

         return true;
      } else {
         DataBufferUtils.release(remainder);
         return false;
      }
   }

   void emitHeaders(HttpHeaders headers) {
      if (logger.isTraceEnabled()) {
         logger.trace("Emitting headers: " + headers);
      }

      this.sink.next(new MultipartParser.HeadersToken(headers));
   }

   void emitBody(DataBuffer buffer, boolean last) {
      if (logger.isTraceEnabled()) {
         logger.trace("Emitting body: " + buffer);
      }

      this.sink.next(new MultipartParser.BodyToken(buffer, last));
   }

   void emitError(Throwable t) {
      this.cancel();
      this.sink.error(t);
   }

   void emitComplete() {
      this.cancel();
      this.sink.complete();
   }

   private void requestBuffer() {
      if (this.upstream() != null && !this.sink.isCancelled() && this.sink.requestedFromDownstream() > 0L && this.requestOutstanding.compareAndSet(false, true)
         )
       {
         this.request(1L);
      }
   }

   private final class BodyState implements MultipartParser.State {
      private final Matcher boundary;
      private final int boundaryLength;
      private final Deque<DataBuffer> queue = new ConcurrentLinkedDeque<>();

      public BodyState() {
         byte[] delimiter = MultipartUtils.concat(MultipartParser.CR_LF, MultipartParser.TWO_HYPHENS, MultipartParser.this.boundary);
         this.boundary = DataBufferUtils.matcher(delimiter);
         this.boundaryLength = delimiter.length;
      }

      @Override
      public void onNext(DataBuffer buffer) {
         int endIdx = this.boundary.match(buffer);
         if (endIdx != -1) {
            DataBuffer boundaryBuffer = buffer.split(endIdx + 1);
            if (MultipartParser.logger.isTraceEnabled()) {
               MultipartParser.logger.trace("Boundary found @" + endIdx + " in " + buffer);
            }

            int len = endIdx - this.boundaryLength + 1 - boundaryBuffer.readPosition();
            if (len > 0) {
               DataBuffer body = boundaryBuffer.split(len);
               DataBufferUtils.release(boundaryBuffer);
               this.enqueue(body);
               this.flush();
            } else if (len >= 0) {
               DataBufferUtils.release(boundaryBuffer);
               if (this.queue.isEmpty()) {
                  MultipartParser.this.emitBody(buffer.factory().allocateBuffer(0), true);
               } else {
                  this.flush();
               }
            } else {
               DataBufferUtils.release(boundaryBuffer);
               boolean found = false;

               DataBuffer prev;
               while ((prev = this.queue.pollLast()) != null) {
                  int prevByteCount = prev.readableByteCount();
                  int prevLen = prevByteCount + len;
                  if (prevLen >= 0) {
                     DataBuffer body = prev.split(prevLen + prev.readPosition());
                     DataBufferUtils.release(prev);
                     this.enqueue(body);
                     this.flush();
                     found = true;
                     break;
                  }

                  DataBufferUtils.release(prev);
                  len += prevByteCount;
               }

               if (!found) {
                  MultipartParser.this.emitBody(buffer.factory().allocateBuffer(0), true);
               }
            }

            MultipartParser.this.changeState(this, MultipartParser.this.new HeadersState(), buffer);
         } else {
            this.enqueue(buffer);
            MultipartParser.this.requestBuffer();
         }
      }

      private void enqueue(DataBuffer buf) {
         this.queue.add(buf);
         int len = 0;
         Deque<DataBuffer> emit = new ArrayDeque<>();
         Iterator<DataBuffer> iterator = this.queue.descendingIterator();

         while (iterator.hasNext()) {
            DataBuffer previous = iterator.next();
            if (len > this.boundaryLength) {
               emit.addFirst(previous);
               iterator.remove();
            }

            len += previous.readableByteCount();
         }

         emit.forEach(buffer -> MultipartParser.this.emitBody(buffer, false));
      }

      private void flush() {
         DataBuffer buffer;
         while ((buffer = this.queue.poll()) != null) {
            MultipartParser.this.emitBody(buffer, this.queue.isEmpty());
         }
      }

      @Override
      public void onComplete() {
         if (MultipartParser.this.changeState(this, MultipartParser.DisposedState.INSTANCE, null)) {
            String msg = "Could not find end of body (␍␊--" + new String(MultipartParser.this.boundary, StandardCharsets.UTF_8) + ")";
            MultipartParser.this.emitError(new DecodingException(msg));
         }
      }

      @Override
      public void dispose() {
         this.queue.forEach(DataBufferUtils::release);
         this.queue.clear();
      }

      @Override
      public String toString() {
         return "BODY";
      }
   }

   public static final class BodyToken extends MultipartParser.Token {
      private final DataBuffer buffer;
      private final boolean last;

      public BodyToken(DataBuffer buffer, boolean last) {
         this.buffer = buffer;
         this.last = last;
      }

      @Override
      public HttpHeaders headers() {
         throw new IllegalStateException();
      }

      @Override
      public DataBuffer buffer() {
         return this.buffer;
      }

      @Override
      public boolean isLast() {
         return this.last;
      }
   }

   private static final class DisposedState implements MultipartParser.State {
      public static final MultipartParser.DisposedState INSTANCE = new MultipartParser.DisposedState();

      @Override
      public void onNext(DataBuffer buf) {
         DataBufferUtils.release(buf);
      }

      @Override
      public void onComplete() {
      }

      @Override
      public String toString() {
         return "DISPOSED";
      }
   }

   private final class HeadersState implements MultipartParser.State {
      private final Matcher endHeaders = DataBufferUtils.matcher(MultipartUtils.concat(MultipartParser.CR_LF, MultipartParser.CR_LF));
      private final AtomicInteger byteCount = new AtomicInteger();
      private final List<DataBuffer> buffers = new ArrayList<>();

      @Override
      public void onNext(DataBuffer buf) {
         if (this.isLastBoundary(buf)) {
            if (MultipartParser.logger.isTraceEnabled()) {
               MultipartParser.logger.trace("Last boundary found in " + buf);
            }

            if (MultipartParser.this.changeState(this, MultipartParser.DisposedState.INSTANCE, buf)) {
               MultipartParser.this.emitComplete();
            }
         } else {
            int endIdx = this.endHeaders.match(buf);
            if (endIdx != -1) {
               if (MultipartParser.logger.isTraceEnabled()) {
                  MultipartParser.logger.trace("End of headers found @" + endIdx + " in " + buf);
               }

               long count = this.byteCount.addAndGet(endIdx);
               if (this.belowMaxHeaderSize(count)) {
                  DataBuffer headerBuf = buf.split(endIdx + 1);
                  this.buffers.add(headerBuf);
                  MultipartParser.this.emitHeaders(this.parseHeaders());
                  MultipartParser.this.changeState(this, MultipartParser.this.new BodyState(), buf);
               } else {
                  MultipartParser.this.changeState(this, MultipartParser.DisposedState.INSTANCE, buf);
               }
            } else {
               long count = this.byteCount.addAndGet(buf.readableByteCount());
               if (this.belowMaxHeaderSize(count)) {
                  this.buffers.add(buf);
                  MultipartParser.this.requestBuffer();
               } else {
                  MultipartParser.this.changeState(this, MultipartParser.DisposedState.INSTANCE, buf);
               }
            }
         }
      }

      private boolean isLastBoundary(DataBuffer buf) {
         return this.buffers.isEmpty() && buf.readableByteCount() >= 2 && buf.getByte(0) == 45 && buf.getByte(1) == 45
            || this.buffers.size() == 1
               && this.buffers.get(0).readableByteCount() == 1
               && this.buffers.get(0).getByte(0) == 45
               && buf.readableByteCount() >= 1
               && buf.getByte(0) == 45;
      }

      private boolean belowMaxHeaderSize(long count) {
         if (count <= MultipartParser.this.maxHeadersSize) {
            return true;
         }

         MultipartParser.this.emitError(
            new DataBufferLimitException("Part headers exceeded the memory usage limit of " + MultipartParser.this.maxHeadersSize + " bytes")
         );
         return false;
      }

      private HttpHeaders parseHeaders() {
         if (this.buffers.isEmpty()) {
            return HttpHeaders.EMPTY;
         }

         DataBuffer joined = this.buffers.get(0).factory().join(this.buffers);
         this.buffers.clear();
         String string = joined.toString(MultipartParser.this.headersCharset);
         DataBufferUtils.release(joined);
         String[] lines = string.split("\\r\\n");
         HttpHeaders result = new HttpHeaders();

         for (String line : lines) {
            int idx = line.indexOf(58);
            if (idx != -1) {
               String name = line.substring(0, idx);
               String value = line.substring(idx + 1);

               while (value.startsWith(" ")) {
                  value = value.substring(1);
               }

               result.add(name, value);
            }
         }

         return result;
      }

      @Override
      public void onComplete() {
         if (MultipartParser.this.changeState(this, MultipartParser.DisposedState.INSTANCE, null)) {
            MultipartParser.this.emitError(new DecodingException("Could not find end of headers"));
         }
      }

      @Override
      public void dispose() {
         this.buffers.forEach(DataBufferUtils::release);
      }

      @Override
      public String toString() {
         return "HEADERS";
      }
   }

   public static final class HeadersToken extends MultipartParser.Token {
      private final HttpHeaders headers;

      public HeadersToken(HttpHeaders headers) {
         this.headers = headers;
      }

      @Override
      public HttpHeaders headers() {
         return this.headers;
      }

      @Override
      public DataBuffer buffer() {
         throw new IllegalStateException();
      }

      @Override
      public boolean isLast() {
         return false;
      }
   }

   private final class PreambleState implements MultipartParser.State {
      private final Matcher firstBoundary = DataBufferUtils.matcher(MultipartUtils.concat(MultipartParser.TWO_HYPHENS, MultipartParser.this.boundary));

      public PreambleState() {
      }

      @Override
      public void onNext(DataBuffer buf) {
         int endIdx = this.firstBoundary.match(buf);
         if (endIdx != -1) {
            if (MultipartParser.logger.isTraceEnabled()) {
               MultipartParser.logger.trace("First boundary found @" + endIdx + " in " + buf);
            }

            DataBuffer preambleBuffer = buf.split(endIdx + 1);
            DataBufferUtils.release(preambleBuffer);
            MultipartParser.this.changeState(this, MultipartParser.this.new HeadersState(), buf);
         } else {
            DataBufferUtils.release(buf);
            MultipartParser.this.requestBuffer();
         }
      }

      @Override
      public void onComplete() {
         if (MultipartParser.this.changeState(this, MultipartParser.DisposedState.INSTANCE, null)) {
            MultipartParser.this.emitError(new DecodingException("Could not find first boundary"));
         }
      }

      @Override
      public String toString() {
         return "PREAMBLE";
      }
   }

   private interface State {
      void onNext(DataBuffer buf);

      void onComplete();

      default void dispose() {
      }
   }

   public abstract static class Token {
      public abstract HttpHeaders headers();

      public abstract DataBuffer buffer();

      public abstract boolean isLast();
   }
}
