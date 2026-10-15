package org.springframework.http.client.reactive;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.Function;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.message.BasicHttpRequest;
import org.apache.hc.core5.http.nio.AsyncRequestProducer;
import org.apache.hc.core5.http.nio.support.BasicRequestProducer;
import org.apache.hc.core5.reactive.ReactiveEntityProducer;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.support.HttpComponentsHeadersAdapter;
import org.springframework.util.CollectionUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class HttpComponentsClientHttpRequest extends AbstractClientHttpRequest {
   private final HttpRequest httpRequest;
   private final DataBufferFactory dataBufferFactory;
   private final HttpClientContext context;
   private @Nullable Flux<ByteBuffer> byteBufferFlux;
   private transient long contentLength = -1L;

   public HttpComponentsClientHttpRequest(HttpMethod method, URI uri, HttpClientContext context, DataBufferFactory dataBufferFactory) {
      this.context = context;
      this.httpRequest = new BasicHttpRequest(method.name(), uri);
      this.dataBufferFactory = dataBufferFactory;
   }

   @Override
   public HttpMethod getMethod() {
      return HttpMethod.valueOf(this.httpRequest.getMethod());
   }

   @Override
   public URI getURI() {
      try {
         return this.httpRequest.getUri();
      } catch (URISyntaxException ex) {
         throw new IllegalArgumentException("Invalid URI syntax: " + ex.getMessage());
      }
   }

   @Override
   public DataBufferFactory bufferFactory() {
      return this.dataBufferFactory;
   }

   @Override
   public <T> T getNativeRequest() {
      return (T)this.httpRequest;
   }

   @Override
   public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
      return this.doCommit(() -> {
         this.byteBufferFlux = Flux.from(body).map(dataBuffer -> {
            ByteBuffer byteBuffer = ByteBuffer.allocate(dataBuffer.readableByteCount());
            dataBuffer.toByteBuffer(byteBuffer);
            return byteBuffer;
         });
         return Mono.empty();
      });
   }

   @Override
   public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
      return this.writeWith(Flux.from(body).flatMap(Function.identity()));
   }

   @Override
   public Mono<Void> setComplete() {
      return this.doCommit();
   }

   @Override
   protected void applyHeaders() {
      HttpHeaders headers = this.getHeaders();
      headers.headerSet()
         .stream()
         .filter(entry -> !"Content-Length".equals(entry.getKey()))
         .forEach(entry -> entry.getValue().forEach(v -> this.httpRequest.addHeader(entry.getKey(), v)));
      if (!this.httpRequest.containsHeader("Accept")) {
         this.httpRequest.addHeader("Accept", "*/*");
      }

      this.contentLength = headers.getContentLength();
   }

   @Override
   protected void applyCookies() {
      if (!this.getCookies().isEmpty()) {
         if (!CollectionUtils.isEmpty(this.getCookies())) {
            this.httpRequest.setHeader("Cookie", this.serializeCookies());
         }
      }
   }

   private String serializeCookies() {
      boolean first = true;
      StringBuilder sb = new StringBuilder();

      for (List<HttpCookie> cookies : this.getCookies().values()) {
         for (HttpCookie cookie : cookies) {
            if (!first) {
               sb.append("; ");
            } else {
               first = false;
            }

            sb.append(cookie.getName()).append("=").append(cookie.getValue());
         }
      }

      return sb.toString();
   }

   @Override
   protected void applyAttributes() {
      this.getAttributes().forEach((key, value) -> {
         if (this.context.getAttribute(key) == null) {
            this.context.setAttribute(key, value);
         }
      });
   }

   @Override
   protected HttpHeaders initReadOnlyHeaders() {
      return HttpHeaders.readOnlyHttpHeaders(new HttpComponentsHeadersAdapter(this.httpRequest));
   }

   public AsyncRequestProducer toRequestProducer() {
      ReactiveEntityProducer reactiveEntityProducer = null;
      if (this.byteBufferFlux != null) {
         String contentEncoding = this.getHeaders().getFirst("Content-Encoding");
         ContentType contentType = null;
         if (this.getHeaders().getContentType() != null) {
            contentType = ContentType.parse(this.getHeaders().getContentType().toString());
         }

         reactiveEntityProducer = new ReactiveEntityProducer(this.byteBufferFlux, this.contentLength, contentType, contentEncoding);
      }

      return new BasicRequestProducer(this.httpRequest, reactiveEntityProducer);
   }
}
