package org.springframework.http.client.reactive;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpRequest.Builder;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpMethod;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import reactor.adapter.JdkFlowAdapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class JdkClientHttpRequest extends AbstractClientHttpRequest {
   private final HttpMethod method;
   private final URI uri;
   private final DataBufferFactory bufferFactory;
   private final Builder builder;

   public JdkClientHttpRequest(HttpMethod httpMethod, URI uri, DataBufferFactory bufferFactory, @Nullable Duration readTimeout) {
      Assert.notNull(httpMethod, "HttpMethod is required");
      Assert.notNull(uri, "URI is required");
      Assert.notNull(bufferFactory, "DataBufferFactory is required");
      this.method = httpMethod;
      this.uri = uri;
      this.bufferFactory = bufferFactory;
      this.builder = HttpRequest.newBuilder(uri);
      if (readTimeout != null) {
         this.builder.timeout(readTimeout);
      }
   }

   @Override
   public HttpMethod getMethod() {
      return this.method;
   }

   @Override
   public URI getURI() {
      return this.uri;
   }

   @Override
   public DataBufferFactory bufferFactory() {
      return this.bufferFactory;
   }

   @Override
   public <T> T getNativeRequest() {
      return (T)this.builder.build();
   }

   @Override
   public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
      return this.doCommit(() -> {
         this.builder.method(this.method.name(), this.toBodyPublisher(body));
         return Mono.empty();
      });
   }

   private BodyPublisher toBodyPublisher(Publisher<? extends DataBuffer> body) {
      Publisher<ByteBuffer> byteBufferBody = (Publisher<ByteBuffer>)(body instanceof Mono
         ? Mono.from(body).map(this::toByteBuffer)
         : Flux.from(body).map(this::toByteBuffer));
      java.util.concurrent.Flow.Publisher<ByteBuffer> bodyFlow = JdkFlowAdapter.publisherToFlowPublisher(byteBufferBody);
      return this.getHeaders().getContentLength() > 0L
         ? BodyPublishers.fromPublisher(bodyFlow, this.getHeaders().getContentLength())
         : BodyPublishers.fromPublisher(bodyFlow);
   }

   private ByteBuffer toByteBuffer(DataBuffer dataBuffer) {
      ByteBuffer byteBuffer = ByteBuffer.allocate(dataBuffer.readableByteCount());
      dataBuffer.toByteBuffer(byteBuffer);
      return byteBuffer;
   }

   @Override
   public Mono<Void> writeAndFlushWith(final Publisher<? extends Publisher<? extends DataBuffer>> body) {
      return this.writeWith(Flux.from(body).flatMap(Function.identity()));
   }

   @Override
   public Mono<Void> setComplete() {
      return this.doCommit(() -> {
         this.builder.method(this.method.name(), BodyPublishers.noBody());
         return Mono.empty();
      });
   }

   @Override
   protected void applyHeaders() {
      for (Entry<String, List<String>> entry : this.getHeaders().headerSet()) {
         if (!entry.getKey().equalsIgnoreCase("Content-Length")) {
            for (String value : entry.getValue()) {
               this.builder.header(entry.getKey(), value);
            }
         }
      }

      if (!this.getHeaders().containsHeader("Accept")) {
         this.builder.header("Accept", "*/*");
      }
   }

   @Override
   protected void applyCookies() {
      MultiValueMap<String, HttpCookie> cookies = this.getCookies();
      if (!cookies.isEmpty()) {
         this.builder.header("Cookie", cookies.values().stream().flatMap(Collection::stream).map(HttpCookie::toString).collect(Collectors.joining(";")));
      }
   }
}
