package org.springframework.http.converter.multipart;

import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils.Matcher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.converter.HttpMessageConversionException;

final class MultipartParser {
   private static final byte[] CR_LF = new byte[]{13, 10};
   private static final byte HYPHEN = 45;
   private static final byte[] TWO_HYPHENS = new byte[]{45, 45};
   private static final String HEADER_ENTRY_SEPARATOR = "\\r\\n";
   private static final Log logger = LogFactory.getLog(MultipartParser.class);
   private final byte[] boundary;
   private final Charset headersCharset;
   private final int maxHeadersSize;
   private final MultipartParser.PartListener listener;
   private MultipartParser.State state;

   private MultipartParser(byte[] boundary, Charset headersCharset, int maxHeadersSize, MultipartParser.PartListener listener) {
      this.boundary = boundary;
      this.headersCharset = headersCharset;
      this.maxHeadersSize = maxHeadersSize;
      this.listener = listener;
      this.state = new MultipartParser.PreambleState();
   }

   void handleData(DataBuffer dataBuffer) {
      this.state.data(dataBuffer);
   }

   void handleException(String message, @Nullable Throwable cause) {
      if (cause instanceof HttpMessageConversionException ex) {
         throw ex;
      } else {
         this.changeState(MultipartParser.DisposedState.INSTANCE, null);
         HttpMessageConversionException ex = new HttpMessageConversionException(message, cause);
         this.listener.onError(ex);
         throw ex;
      }
   }

   private void changeState(MultipartParser.State newState, @Nullable DataBuffer remainder) {
      if (logger.isTraceEnabled()) {
         logger.trace("Changed state: " + this.state + " -> " + newState);
      }

      this.state.dispose();
      this.state = newState;
      if (remainder != null) {
         if (remainder.readableByteCount() > 0) {
            newState.data(remainder);
         } else {
            DataBufferUtils.release(remainder);
         }
      }
   }

   private static byte[] concat(byte[]... byteArrays) {
      int len = 0;

      for (byte[] byteArray : byteArrays) {
         len += byteArray.length;
      }

      byte[] result = new byte[len];
      len = 0;

      for (byte[] byteArray : byteArrays) {
         System.arraycopy(byteArray, 0, result, len, byteArray.length);
         len += byteArray.length;
      }

      return result;
   }

   public static void parse(
      InputStream input, byte[] boundary, Charset headersCharset, int maxHeadersSize, int bufferSize, MultipartParser.PartListener listener
   ) {
      MultipartParser parser = new MultipartParser(boundary, headersCharset, maxHeadersSize, listener);

      try {
         while (true) {
            byte[] read = input.readNBytes(bufferSize);
            if (read.length == 0) {
               parser.state.complete();
               break;
            }

            parser.handleData(DefaultDataBufferFactory.sharedInstance.wrap(read));
         }
      } catch (Throwable ex) {
         parser.handleException("Could not decode multipart message", ex);
      }
   }

   private final class BodyState implements MultipartParser.State {
      private final Matcher boundaryMatcher;
      private final int boundaryLength;
      private final Deque<DataBuffer> queue = new ArrayDeque<>();

      public BodyState() {
         byte[] delimiter = MultipartParser.concat(MultipartParser.CR_LF, MultipartParser.TWO_HYPHENS, MultipartParser.this.boundary);
         this.boundaryMatcher = DataBufferUtils.matcher(delimiter);
         this.boundaryLength = delimiter.length;
      }

      @Override
      public void data(DataBuffer buffer) {
         int endIdx = this.boundaryMatcher.match(buffer);
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
                  this.invokeListener(buffer.factory().allocateBuffer(0), true);
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
                  this.invokeListener(buffer.factory().allocateBuffer(0), true);
               }
            }

            MultipartParser.this.changeState(MultipartParser.this.new HeadersState(), buffer);
         } else {
            this.enqueue(buffer);
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

         emit.forEach(buffer -> this.invokeListener(buffer, false));
      }

      private void flush() {
         Iterator<DataBuffer> iterator = this.queue.iterator();

         while (iterator.hasNext()) {
            DataBuffer buffer = iterator.next();
            boolean last = !iterator.hasNext();
            this.invokeListener(buffer, last);
         }

         this.queue.clear();
      }

      private void invokeListener(DataBuffer buffer, boolean last) {
         try {
            MultipartParser.this.listener.onBody(buffer, last);
         } catch (Throwable ex) {
            this.dispose();
            throw ex;
         }
      }

      @Override
      public void complete() {
         MultipartParser.this.handleException(
            "Could not find end of body (␍␊--" + new String(MultipartParser.this.boundary, StandardCharsets.UTF_8) + ")", null
         );
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

   private static final class DisposedState implements MultipartParser.State {
      public static final MultipartParser.DisposedState INSTANCE = new MultipartParser.DisposedState();

      @Override
      public void data(DataBuffer buf) {
         DataBufferUtils.release(buf);
      }

      @Override
      public void complete() {
      }

      @Override
      public String toString() {
         return "DISPOSED";
      }
   }

   private final class HeadersState implements MultipartParser.State {
      private final Matcher endHeaders = DataBufferUtils.matcher(MultipartParser.concat(MultipartParser.CR_LF, MultipartParser.CR_LF));
      private final List<DataBuffer> buffers = new ArrayList<>();
      private int byteCount;

      @Override
      public void data(DataBuffer buf) {
         if (this.isLastBoundary(buf)) {
            if (MultipartParser.logger.isTraceEnabled()) {
               MultipartParser.logger.trace("Last boundary found in " + buf);
            }

            MultipartParser.this.changeState(MultipartParser.DisposedState.INSTANCE, buf);
            MultipartParser.this.listener.onComplete();
         } else {
            int endIdx = this.endHeaders.match(buf);
            if (endIdx != -1) {
               if (MultipartParser.logger.isTraceEnabled()) {
                  MultipartParser.logger.trace("End of headers found @" + endIdx + " in " + buf);
               }

               this.byteCount += endIdx;
               if (this.belowMaxHeaderSize(this.byteCount)) {
                  DataBuffer headerBuf = buf.split(endIdx + 1);
                  this.buffers.add(headerBuf);
                  this.emitHeaders();
                  MultipartParser.this.changeState(MultipartParser.this.new BodyState(), buf);
               }
            } else {
               this.byteCount = this.byteCount + buf.readableByteCount();
               if (this.belowMaxHeaderSize(this.byteCount)) {
                  this.buffers.add(buf);
               }
            }
         }
      }

      private void emitHeaders() {
         HttpHeaders headers = this.parseHeaders();
         if (MultipartParser.logger.isTraceEnabled()) {
            MultipartParser.logger.trace("Emitting headers: " + headers);
         }

         try {
            MultipartParser.this.listener.onHeaders(headers);
         } catch (Throwable ex) {
            this.dispose();
            throw ex;
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

         MultipartParser.this.handleException("Part headers exceeded the limit of " + MultipartParser.this.maxHeadersSize + " bytes", null);
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
      public void complete() {
         MultipartParser.this.handleException("Could not find end of headers", null);
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

   interface PartListener {
      void onHeaders(HttpHeaders headers);

      void onBody(DataBuffer buffer, boolean last);

      void onComplete();

      void onError(Throwable error);
   }

   private final class PreambleState implements MultipartParser.State {
      private final Matcher firstBoundary = DataBufferUtils.matcher(MultipartParser.concat(MultipartParser.TWO_HYPHENS, MultipartParser.this.boundary));

      PreambleState() {
      }

      @Override
      public void data(DataBuffer buf) {
         int endIdx = this.firstBoundary.match(buf);
         if (endIdx != -1) {
            if (MultipartParser.logger.isTraceEnabled()) {
               MultipartParser.logger.trace("First boundary found @" + endIdx + " in " + buf);
            }

            DataBuffer preambleBuffer = buf.split(endIdx + 1);
            DataBufferUtils.release(preambleBuffer);
            MultipartParser.this.changeState(MultipartParser.this.new HeadersState(), buf);
         } else {
            DataBufferUtils.release(buf);
         }
      }

      @Override
      public void complete() {
         MultipartParser.this.handleException("Could not find first boundary", null);
      }

      @Override
      public String toString() {
         return "PREAMBLE";
      }
   }

   private interface State {
      void data(DataBuffer buf);

      void complete();

      default void dispose() {
      }
   }
}
