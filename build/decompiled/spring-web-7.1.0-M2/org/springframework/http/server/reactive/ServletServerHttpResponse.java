package org.springframework.http.server.reactive;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DataBuffer.ByteBufferIterator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.util.Assert;

class ServletServerHttpResponse extends AbstractListenerServerHttpResponse {
   private final HttpServletResponse response;
   private final ServletOutputStream outputStream;
   private volatile ServletServerHttpResponse.@Nullable ResponseBodyFlushProcessor bodyFlushProcessor;
   private volatile ServletServerHttpResponse.@Nullable ResponseBodyProcessor bodyProcessor;
   private volatile boolean flushOnNext;
   private final ServletServerHttpRequest request;
   private final ServletServerHttpResponse.ResponseAsyncListener asyncListener;

   public ServletServerHttpResponse(HttpServletResponse response, AsyncContext asyncContext, DataBufferFactory bufferFactory, ServletServerHttpRequest request) throws IOException {
      this(new HttpHeaders(), response, asyncContext, bufferFactory, request);
   }

   public ServletServerHttpResponse(
      HttpHeaders headers, HttpServletResponse response, AsyncContext asyncContext, DataBufferFactory bufferFactory, ServletServerHttpRequest request
   ) throws IOException {
      super(bufferFactory, headers);
      Assert.notNull(response, "HttpServletResponse must not be null");
      Assert.notNull(bufferFactory, "DataBufferFactory must not be null");
      this.response = response;
      this.outputStream = response.getOutputStream();
      this.request = request;
      this.asyncListener = new ServletServerHttpResponse.ResponseAsyncListener();
      response.getOutputStream().setWriteListener(new ServletServerHttpResponse.ResponseBodyWriteListener());
   }

   @Override
   public <T> T getNativeResponse() {
      return (T)this.response;
   }

   @Override
   public HttpStatusCode getStatusCode() {
      HttpStatusCode status = super.getStatusCode();
      return status != null ? status : HttpStatusCode.valueOf(this.response.getStatus());
   }

   @Override
   protected void applyStatusCode() {
      HttpStatusCode status = super.getStatusCode();
      if (status != null) {
         this.response.setStatus(status.value());
      }
   }

   @Override
   protected void applyHeaders() {
      this.getHeaders().forEach((headerName, headerValues) -> {
         for (String headerValue : headerValues) {
            this.response.addHeader(headerName, headerValue);
         }
      });
      this.adaptHeaders(false);
   }

   protected void adaptHeaders(boolean removeAdaptedHeaders) {
      HttpHeaders headers = this.getHeaders();
      if (this.response.getContentType() == null && headers.containsHeader("Content-Type")) {
         this.response.setContentType(headers.getFirst("Content-Type"));
      }

      if (this.response.getCharacterEncoding() == null && headers.containsHeader("Content-Type")) {
         try {
            MediaType contentType = headers.getContentType();
            if (contentType != null) {
               Charset charset = contentType.getCharset();
               if (charset != null) {
                  this.response.setCharacterEncoding(charset);
               }
            }
         } catch (Exception var5) {
         }
      }

      long contentLength = headers.getContentLength();
      if (contentLength != -1L) {
         this.response.setContentLengthLong(contentLength);
      }

      if (removeAdaptedHeaders) {
         headers.remove("Content-Type");
         headers.remove("Content-Length");
      }
   }

   @Override
   protected void applyCookies() {
      for (String name : this.getCookies().keySet()) {
         for (ResponseCookie httpCookie : (List)this.getCookies().get(name)) {
            Cookie cookie = new Cookie(name, httpCookie.getValue());
            if (!httpCookie.getMaxAge().isNegative()) {
               cookie.setMaxAge((int)httpCookie.getMaxAge().getSeconds());
            }

            if (httpCookie.getDomain() != null) {
               cookie.setDomain(httpCookie.getDomain());
            }

            if (httpCookie.getPath() != null) {
               cookie.setPath(httpCookie.getPath());
            }

            if (httpCookie.getSameSite() != null) {
               cookie.setAttribute("SameSite", httpCookie.getSameSite());
            }

            cookie.setSecure(httpCookie.isSecure());
            cookie.setHttpOnly(httpCookie.isHttpOnly());
            if (httpCookie.isPartitioned()) {
               cookie.setAttribute("Partitioned", "");
            }

            this.response.addCookie(cookie);
         }
      }
   }

   AsyncListener getAsyncListener() {
      return this.asyncListener;
   }

   @Override
   protected Processor<? super Publisher<? extends DataBuffer>, Void> createBodyFlushProcessor() {
      ServletServerHttpResponse.ResponseBodyFlushProcessor processor = new ServletServerHttpResponse.ResponseBodyFlushProcessor();
      this.bodyFlushProcessor = processor;
      return processor;
   }

   protected final ServletOutputStream getOutputStream() {
      return this.outputStream;
   }

   protected int writeToOutputStream(DataBuffer dataBuffer) throws IOException {
      ServletOutputStream outputStream = this.outputStream;
      int len = 0;
      ByteBufferIterator iterator = dataBuffer.readableByteBuffers();

      try {
         while (iterator.hasNext() && outputStream.isReady()) {
            ByteBuffer byteBuffer = (ByteBuffer)iterator.next();
            len += byteBuffer.remaining();
            outputStream.write(byteBuffer);
         }
      } catch (Throwable var8) {
         if (iterator != null) {
            try {
               iterator.close();
            } catch (Throwable var7) {
               var8.addSuppressed(var7);
            }
         }

         throw var8;
      }

      if (iterator != null) {
         iterator.close();
      }

      return len;
   }

   private void flush() throws IOException {
      ServletOutputStream outputStream = this.outputStream;
      if (outputStream.isReady()) {
         try {
            outputStream.flush();
            this.flushOnNext = false;
         } catch (IOException ex) {
            this.flushOnNext = true;
            throw ex;
         }
      } else {
         this.flushOnNext = true;
      }
   }

   private boolean isWritePossible() {
      return this.outputStream.isReady();
   }

   private final class ResponseAsyncListener implements AsyncListener {
      public void onStartAsync(AsyncEvent event) {
      }

      public void onTimeout(AsyncEvent event) {
         Throwable ex = event.getThrowable();
         ex = ex != null ? ex : new IllegalStateException("Async operation timeout.");
         this.handleError(ex);
      }

      public void onError(AsyncEvent event) {
         this.handleError(event.getThrowable());
      }

      public void handleError(Throwable ex) {
         ServletServerHttpResponse.ResponseBodyFlushProcessor flushProcessor = ServletServerHttpResponse.this.bodyFlushProcessor;
         ServletServerHttpResponse.ResponseBodyProcessor processor = ServletServerHttpResponse.this.bodyProcessor;
         if (flushProcessor != null) {
            flushProcessor.cancel();
            if (processor != null) {
               processor.cancel();
               processor.onError(ex);
            }

            flushProcessor.onError(ex);
         }
      }

      public void onComplete(AsyncEvent event) {
         ServletServerHttpResponse.ResponseBodyFlushProcessor flushProcessor = ServletServerHttpResponse.this.bodyFlushProcessor;
         ServletServerHttpResponse.ResponseBodyProcessor processor = ServletServerHttpResponse.this.bodyProcessor;
         if (flushProcessor != null) {
            flushProcessor.cancel();
            if (processor != null) {
               processor.cancel();
               processor.onComplete();
            }

            flushProcessor.onComplete();
         }
      }
   }

   private class ResponseBodyFlushProcessor extends AbstractListenerWriteFlushProcessor<DataBuffer> {
      public ResponseBodyFlushProcessor() {
         super(ServletServerHttpResponse.this.request.getLogPrefix());
      }

      @Override
      protected Processor<? super DataBuffer, Void> createWriteProcessor() {
         ServletServerHttpResponse.ResponseBodyProcessor processor = ServletServerHttpResponse.this.new ResponseBodyProcessor();
         ServletServerHttpResponse.this.bodyProcessor = processor;
         return processor;
      }

      @Override
      protected void flush() throws IOException {
         if (rsWriteFlushLogger.isTraceEnabled()) {
            rsWriteFlushLogger.trace(this.getLogPrefix() + "flushing");
         }

         ServletServerHttpResponse.this.flush();
      }

      @Override
      protected boolean isWritePossible() {
         return ServletServerHttpResponse.this.isWritePossible();
      }

      @Override
      protected boolean isFlushPending() {
         return ServletServerHttpResponse.this.flushOnNext;
      }
   }

   private class ResponseBodyProcessor extends AbstractListenerWriteProcessor<DataBuffer> {
      public ResponseBodyProcessor() {
         super(ServletServerHttpResponse.this.request.getLogPrefix());
      }

      @Override
      protected boolean isWritePossible() {
         return ServletServerHttpResponse.this.isWritePossible();
      }

      protected boolean isDataEmpty(DataBuffer dataBuffer) {
         return dataBuffer.readableByteCount() == 0;
      }

      protected boolean write(DataBuffer dataBuffer) throws IOException {
         if (ServletServerHttpResponse.this.flushOnNext) {
            if (rsWriteLogger.isTraceEnabled()) {
               rsWriteLogger.trace(this.getLogPrefix() + "flushing");
            }

            ServletServerHttpResponse.this.flush();
         }

         boolean ready = ServletServerHttpResponse.this.isWritePossible();
         int remaining = dataBuffer.readableByteCount();
         if (ready && remaining > 0) {
            int written = ServletServerHttpResponse.this.writeToOutputStream(dataBuffer);
            if (rsWriteLogger.isTraceEnabled()) {
               rsWriteLogger.trace(this.getLogPrefix() + "Wrote " + written + " of " + remaining + " bytes");
            }

            if (written == remaining) {
               DataBufferUtils.release(dataBuffer);
               return true;
            }
         } else if (rsWriteLogger.isTraceEnabled()) {
            rsWriteLogger.trace(this.getLogPrefix() + "ready: " + ready + ", remaining: " + remaining);
         }

         return false;
      }

      @Override
      protected void writingComplete() {
         ServletServerHttpResponse.this.bodyProcessor = null;
      }

      protected void discardData(DataBuffer dataBuffer) {
         DataBufferUtils.release(dataBuffer);
      }
   }

   private class ResponseBodyWriteListener implements WriteListener {
      public void onWritePossible() {
         ServletServerHttpResponse.ResponseBodyProcessor processor = ServletServerHttpResponse.this.bodyProcessor;
         if (processor != null) {
            processor.onWritePossible();
         } else {
            ServletServerHttpResponse.ResponseBodyFlushProcessor flushProcessor = ServletServerHttpResponse.this.bodyFlushProcessor;
            if (flushProcessor != null) {
               flushProcessor.onFlushPossible();
            }
         }
      }

      public void onError(Throwable ex) {
         ServletServerHttpResponse.this.asyncListener.handleError(ex);
      }
   }
}
