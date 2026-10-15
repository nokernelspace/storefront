package org.springframework.http.client.reactive;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import java.net.URI;
import java.nio.file.Path;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ZeroCopyHttpOutputMessage;
import org.springframework.http.support.Netty4HeadersAdapter;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.NettyOutbound;
import reactor.netty.channel.ChannelOperations;
import reactor.netty.http.client.HttpClientRequest;

class ReactorClientHttpRequest extends AbstractClientHttpRequest implements ZeroCopyHttpOutputMessage {
   private final HttpMethod httpMethod;
   private final URI uri;
   private final HttpClientRequest request;
   private final NettyOutbound outbound;
   private final NettyDataBufferFactory bufferFactory;

   public ReactorClientHttpRequest(HttpMethod method, URI uri, HttpClientRequest request, NettyOutbound outbound) {
      this.httpMethod = method;
      this.uri = uri;
      this.request = request;
      this.outbound = outbound;
      this.bufferFactory = new NettyDataBufferFactory(outbound.alloc());
   }

   @Override
   public HttpMethod getMethod() {
      return this.httpMethod;
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
      return (T)this.request;
   }

   @Override
   public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
      return this.doCommit(() -> {
         if (body instanceof Mono) {
            Mono<ByteBuf> byteBufMono = Mono.from(body).map(NettyDataBufferFactory::toByteBuf);
            return this.outbound.send(byteBufMono).then();
         } else {
            Flux<ByteBuf> byteBufFlux = Flux.from(body).map(NettyDataBufferFactory::toByteBuf);
            return this.outbound.send(byteBufFlux).then();
         }
      });
   }

   @Override
   public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
      Publisher<Publisher<ByteBuf>> byteBufs = Flux.from(body).map(ReactorClientHttpRequest::toByteBufs);
      return this.doCommit(() -> this.outbound.sendGroups(byteBufs).then());
   }

   private static Publisher<ByteBuf> toByteBufs(Publisher<? extends DataBuffer> dataBuffers) {
      return Flux.from(dataBuffers).map(NettyDataBufferFactory::toByteBuf);
   }

   @Override
   public Mono<Void> writeWith(Path file, long position, long count) {
      return this.doCommit(() -> this.outbound.sendFile(file, position, count).then());
   }

   @Override
   public Mono<Void> setComplete() {
      return this.doCommit(null);
   }

   @Override
   protected void applyHeaders() {
      this.getHeaders().forEach((key, value) -> this.request.requestHeaders().set(key, value));
   }

   @Override
   protected void applyCookies() {
      this.getCookies().values().forEach(values -> values.forEach(value -> {
         DefaultCookie cookie = new DefaultCookie(value.getName(), value.getValue());
         this.request.addCookie(cookie);
      }));
   }

   @Override
   protected void applyAttributes() {
      if (!this.getAttributes().isEmpty()) {
         ((ChannelOperations)this.request).channel().attr(ReactorClientHttpConnector.ATTRIBUTES_KEY).set(this.getAttributes());
      }
   }

   @Override
   protected HttpHeaders initReadOnlyHeaders() {
      return HttpHeaders.readOnlyHttpHeaders(new Netty4HeadersAdapter(this.request.requestHeaders()));
   }
}
