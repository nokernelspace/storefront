package org.springframework.http.client.reactive;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.http.HttpCookie;
import org.eclipse.jetty.io.Content.Chunk;
import org.eclipse.jetty.reactive.client.ReactiveRequest;
import org.eclipse.jetty.reactive.client.ReactiveRequest.Builder;
import org.eclipse.jetty.reactive.client.ReactiveRequest.Content;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DataBuffer.ByteBufferIterator;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.support.JettyHeadersAdapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class JettyClientHttpRequest extends AbstractClientHttpRequest {
   private final Request jettyRequest;
   private final DataBufferFactory bufferFactory;
   private final Builder builder;

   public JettyClientHttpRequest(Request jettyRequest, DataBufferFactory bufferFactory) {
      this.jettyRequest = jettyRequest;
      this.bufferFactory = bufferFactory;
      this.builder = ReactiveRequest.newBuilder(this.jettyRequest).abortOnCancel(true);
   }

   @Override
   public HttpMethod getMethod() {
      return HttpMethod.valueOf(this.jettyRequest.getMethod());
   }

   @Override
   public URI getURI() {
      return this.jettyRequest.getURI();
   }

   @Override
   public DataBufferFactory bufferFactory() {
      return this.bufferFactory;
   }

   @Override
   public <T> T getNativeRequest() {
      return (T)this.jettyRequest;
   }

   @Override
   protected HttpHeaders initReadOnlyHeaders() {
      return HttpHeaders.readOnlyHttpHeaders(new JettyHeadersAdapter(this.jettyRequest.getHeaders()));
   }

   @Override
   public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
      return Mono.create(
            sink -> {
               Content content = (Content)Flux.from(body)
                  .concatMapIterable(this::toContentChunks)
                  .concatWith(Mono.just(Chunk.EOF))
                  .doOnError(sink::error)
                  .as(chunks -> Content.fromPublisher(chunks, this.getContentType()));
               this.builder.content(content);
               sink.success();
            }
         )
         .then(this.doCommit());
   }

   @Override
   public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
      return this.writeWith(Flux.from(body).flatMap(Function.identity()).doOnDiscard(DataBuffer.class, DataBufferUtils::release));
   }

   private List<Chunk> toContentChunks(DataBuffer dataBuffer) {
      List<Chunk> result = new ArrayList<>(1);
      ByteBufferIterator iterator = dataBuffer.readableByteBuffers();

      while (iterator.hasNext()) {
         ByteBuffer byteBuffer = (ByteBuffer)iterator.next();
         boolean last = !iterator.hasNext();
         Chunk chunk = Chunk.from(byteBuffer, false, () -> {
            if (last) {
               iterator.close();
               DataBufferUtils.release(dataBuffer);
            }
         });
         result.add(chunk);
      }

      return result;
   }

   private String getContentType() {
      MediaType contentType = this.getHeaders().getContentType();
      return contentType != null ? contentType.toString() : "application/octet-stream";
   }

   @Override
   public Mono<Void> setComplete() {
      return this.doCommit();
   }

   @Override
   protected void applyHeaders() {
      HttpHeaders headers = this.getHeaders();
      this.jettyRequest.headers(fields -> {
         headers.forEach((key, value) -> value.forEach(v -> fields.add(key, v)));
         if (!headers.containsHeader("Accept")) {
            fields.add("Accept", "*/*");
         }
      });
   }

   @Override
   protected void applyCookies() {
      this.getCookies()
         .values()
         .stream()
         .flatMap(Collection::stream)
         .map(cookie -> HttpCookie.build(cookie.getName(), cookie.getValue()).build())
         .forEach(this.jettyRequest::cookie);
   }

   @Override
   protected void applyAttributes() {
      this.getAttributes().forEach((key, value) -> {
         if (this.jettyRequest.getAttributes().get(key) == null) {
            this.jettyRequest.attribute(key, value);
         }
      });
   }

   public ReactiveRequest toReactiveRequest() {
      return this.builder.build();
   }
}
