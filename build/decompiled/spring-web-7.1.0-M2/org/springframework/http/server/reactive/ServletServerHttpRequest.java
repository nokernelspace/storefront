package org.springframework.http.server.reactive;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.core.io.buffer.DataBuffer.ByteBufferIterator;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.util.LinkedCaseInsensitiveMap;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

class ServletServerHttpRequest extends AbstractServerHttpRequest {
   static final DataBuffer EOF_BUFFER = DefaultDataBufferFactory.sharedInstance.allocateBuffer(0);
   private final HttpServletRequest request;
   private final ServletInputStream inputStream;
   private final ServletServerHttpRequest.RequestBodyPublisher bodyPublisher;
   private final Object cookieLock = new Object();
   private final DataBufferFactory bufferFactory;
   private final int bufferSize;
   private final AsyncListener asyncListener;

   public ServletServerHttpRequest(HttpServletRequest request, AsyncContext asyncContext, String servletPath, DataBufferFactory bufferFactory, int bufferSize) throws IOException, URISyntaxException {
      this(createDefaultHttpHeaders(request), request, asyncContext, servletPath, bufferFactory, bufferSize);
   }

   public ServletServerHttpRequest(
      HttpHeaders headers, HttpServletRequest request, AsyncContext asyncContext, String servletPath, DataBufferFactory bufferFactory, int bufferSize
   ) throws IOException, URISyntaxException {
      super(HttpMethod.valueOf(request.getMethod()), initUri(request), request.getContextPath() + servletPath, initHeaders(headers, request));
      Assert.notNull(bufferFactory, "'bufferFactory' must not be null");
      Assert.isTrue(bufferSize > 0, "'bufferSize' must be greater than 0");
      this.request = request;
      this.bufferFactory = bufferFactory;
      this.bufferSize = bufferSize;
      this.asyncListener = new ServletServerHttpRequest.RequestAsyncListener();
      this.inputStream = request.getInputStream();
      this.bodyPublisher = new ServletServerHttpRequest.RequestBodyPublisher(this.inputStream);
      this.bodyPublisher.registerReadListener();
   }

   private static HttpHeaders createDefaultHttpHeaders(HttpServletRequest request) {
      HttpHeaders headers = new HttpHeaders();
      Enumeration<?> names = request.getHeaderNames();

      while (names.hasMoreElements()) {
         String name = (String)names.nextElement();
         Enumeration<?> values = request.getHeaders(name);

         while (values.hasMoreElements()) {
            headers.add(name, (String)values.nextElement());
         }
      }

      return headers;
   }

   private static URI initUri(HttpServletRequest servletRequest) {
      Assert.notNull(servletRequest, "'request' must not be null");
      String urlString = null;
      String query = null;
      boolean hasQuery = false;

      try {
         StringBuffer requestURL = servletRequest.getRequestURL();
         query = servletRequest.getQueryString();
         hasQuery = StringUtils.hasText(query);
         if (hasQuery) {
            requestURL.append('?').append(query);
         }

         urlString = requestURL.toString();
         return new URI(urlString);
      } catch (URISyntaxException ex) {
         if (hasQuery) {
            String requestURL = servletRequest.getRequestURL().toString();

            try {
               return new URI(requestURL + "?" + encodeQuery(query));
            } catch (URISyntaxException ex2) {
               try {
                  return new URI(requestURL);
               } catch (URISyntaxException var8) {
               }
            }
         }

         throw new IllegalStateException("Could not resolve HttpServletRequest as URI: " + urlString, ex);
      }
   }

   private static String encodeQuery(String query) throws URISyntaxException {
      return new URI(null, null, "", query, null).getRawQuery();
   }

   private static HttpHeaders initHeaders(HttpHeaders headerValues, HttpServletRequest request) {
      HttpHeaders headers = null;
      MediaType contentType = null;
      if (!StringUtils.hasLength(headerValues.getFirst("Content-Type"))) {
         String requestContentType = request.getContentType();
         if (StringUtils.hasLength(requestContentType)) {
            contentType = MediaType.parseMediaType(requestContentType);
            headers = new HttpHeaders(headerValues);
            headers.setContentType(contentType);
         }
      }

      if (contentType != null && contentType.getCharset() == null) {
         String encoding = request.getCharacterEncoding();
         if (StringUtils.hasLength(encoding)) {
            Map<String, String> params = new LinkedCaseInsensitiveMap();
            params.putAll(contentType.getParameters());
            params.put("charset", Charset.forName(encoding).toString());
            headers.setContentType(new MediaType(contentType, params));
         }
      }

      if (headerValues.getFirst("Content-Type") == null) {
         int contentLength = request.getContentLength();
         if (contentLength != -1) {
            headers = headers != null ? headers : new HttpHeaders(headerValues);
            headers.setContentLength(contentLength);
         }
      }

      return headers != null ? headers : headerValues;
   }

   @Override
   protected MultiValueMap<String, HttpCookie> initCookies() {
      MultiValueMap<String, HttpCookie> httpCookies = new LinkedMultiValueMap();
      Cookie[] cookies;
      synchronized (this.cookieLock) {
         cookies = this.request.getCookies();
      }

      if (cookies != null) {
         for (Cookie cookie : cookies) {
            String name = cookie.getName();
            HttpCookie httpCookie = new HttpCookie(name, cookie.getValue());
            httpCookies.add(name, httpCookie);
         }
      }

      return httpCookies;
   }

   @Override
   public InetSocketAddress getLocalAddress() {
      return new InetSocketAddress(this.request.getLocalAddr(), this.request.getLocalPort());
   }

   @Override
   public InetSocketAddress getRemoteAddress() {
      return new InetSocketAddress(this.request.getRemoteHost(), this.request.getRemotePort());
   }

   @Override
   protected @Nullable SslInfo initSslInfo() {
      X509Certificate[] certificates = this.getX509Certificates();
      return certificates != null ? new DefaultSslInfo(this.getSslSessionId(), certificates) : null;
   }

   private @Nullable String getSslSessionId() {
      return (String)this.request.getAttribute("jakarta.servlet.request.ssl_session_id");
   }

   private X509Certificate @Nullable [] getX509Certificates() {
      return (X509Certificate[])this.request.getAttribute("jakarta.servlet.request.X509Certificate");
   }

   @Override
   public Flux<DataBuffer> getBody() {
      return Flux.from(this.bodyPublisher);
   }

   @Override
   public <T> T getNativeRequest() {
      return (T)this.request;
   }

   AsyncListener getAsyncListener() {
      return this.asyncListener;
   }

   protected final ServletInputStream getInputStream() {
      return this.inputStream;
   }

   DataBuffer readFromInputStream() throws IOException {
      DataBuffer dataBuffer = this.bufferFactory.allocateBuffer(this.bufferSize);
      int read = -1;

      try {
         ByteBufferIterator iterator = dataBuffer.writableByteBuffers();

         try {
            Assert.state(iterator.hasNext(), "No ByteBuffer available");
            ByteBuffer byteBuffer = (ByteBuffer)iterator.next();
            read = this.inputStream.read(byteBuffer);
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

         this.logBytesRead(read);
         if (read > 0) {
            dataBuffer.writePosition(read);
            return dataBuffer;
         } else {
            return read == -1 ? EOF_BUFFER : AbstractListenerReadPublisher.EMPTY_BUFFER;
         }
      } finally {
         if (read <= 0) {
            DataBufferUtils.release(dataBuffer);
         }
      }
   }

   protected final void logBytesRead(int read) {
      Log rsReadLogger = AbstractListenerReadPublisher.rsReadLogger;
      if (rsReadLogger.isTraceEnabled()) {
         rsReadLogger.trace(this.getLogPrefix() + "Read " + read + (read != -1 ? " bytes" : ""));
      }
   }

   private final class RequestAsyncListener implements AsyncListener {
      public void onStartAsync(AsyncEvent event) {
      }

      public void onTimeout(AsyncEvent event) {
         Throwable ex = event.getThrowable();
         ex = ex != null ? ex : new IllegalStateException("Async operation timeout.");
         ServletServerHttpRequest.this.bodyPublisher.onError(ex);
      }

      public void onError(AsyncEvent event) {
         ServletServerHttpRequest.this.bodyPublisher.onError(event.getThrowable());
      }

      public void onComplete(AsyncEvent event) {
         ServletServerHttpRequest.this.bodyPublisher.onAllDataRead();
      }
   }

   private class RequestBodyPublisher extends AbstractListenerReadPublisher<DataBuffer> {
      private final ServletInputStream inputStream;

      public RequestBodyPublisher(ServletInputStream inputStream) {
         super(ServletServerHttpRequest.this.getLogPrefix());
         this.inputStream = inputStream;
      }

      public void registerReadListener() throws IOException {
         this.inputStream.setReadListener(new ServletServerHttpRequest.RequestBodyPublisher.RequestBodyPublisherReadListener());
      }

      @Override
      protected void checkOnDataAvailable() {
         if (this.inputStream.isReady() && !this.inputStream.isFinished()) {
            this.onDataAvailable();
         }
      }

      protected @Nullable DataBuffer read() throws IOException {
         if (this.inputStream.isReady()) {
            DataBuffer dataBuffer = ServletServerHttpRequest.this.readFromInputStream();
            if (dataBuffer == ServletServerHttpRequest.EOF_BUFFER) {
               this.onAllDataRead();
               dataBuffer = null;
            }

            return dataBuffer;
         } else {
            return null;
         }
      }

      @Override
      protected void readingPaused() {
      }

      @Override
      protected void discardData() {
      }

      private class RequestBodyPublisherReadListener implements ReadListener {
         public void onDataAvailable() throws IOException {
            RequestBodyPublisher.this.onDataAvailable();
         }

         public void onAllDataRead() throws IOException {
            RequestBodyPublisher.this.onAllDataRead();
         }

         public void onError(Throwable throwable) {
            RequestBodyPublisher.this.onError(throwable);
         }
      }
   }
}
