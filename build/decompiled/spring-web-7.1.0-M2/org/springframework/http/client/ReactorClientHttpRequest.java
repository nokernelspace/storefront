package org.springframework.http.client;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.Executor;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.FlowAdapters;
import org.reactivestreams.Publisher;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.StreamingHttpOutputMessage;
import org.springframework.util.StreamUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.netty.NettyOutbound;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientRequest;
import reactor.netty.http.client.HttpClient.RequestSender;

final class ReactorClientHttpRequest extends AbstractStreamingClientHttpRequest {
   private final HttpClient httpClient;
   private final HttpMethod method;
   private final URI uri;
   private final Executor executor;
   private final @Nullable Duration exchangeTimeout;

   public ReactorClientHttpRequest(HttpClient httpClient, HttpMethod method, URI uri) {
      this(httpClient, method, uri, null);
   }

   public ReactorClientHttpRequest(HttpClient httpClient, HttpMethod method, URI uri, @Nullable Executor executor) {
      this(httpClient, method, uri, executor, null);
   }

   ReactorClientHttpRequest(HttpClient httpClient, HttpMethod method, URI uri, @Nullable Executor executor, @Nullable Duration exchangeTimeout) {
      this.httpClient = httpClient;
      this.method = method;
      this.uri = uri;
      this.executor = executor != null ? executor : Schedulers.boundedElastic()::schedule;
      this.exchangeTimeout = exchangeTimeout;
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
   protected ClientHttpResponse executeInternal(HttpHeaders headers, StreamingHttpOutputMessage.@Nullable Body body) throws IOException {
      RequestSender sender = this.httpClient.request(io.netty.handler.codec.http.HttpMethod.valueOf(this.method.name()));
      sender = this.uri.isAbsolute() ? (RequestSender)sender.uri(this.uri) : (RequestSender)sender.uri(this.uri.toString());

      try {
         Mono<ReactorClientHttpResponse> mono = sender.send((request, outbound) -> this.send(headers, body, request, outbound))
            .responseConnection((response, conn) -> Mono.just(new ReactorClientHttpResponse(response, conn)))
            .next();
         ReactorClientHttpResponse clientResponse = this.exchangeTimeout != null
            ? (ReactorClientHttpResponse)mono.block(this.exchangeTimeout)
            : (ReactorClientHttpResponse)mono.block();
         if (clientResponse == null) {
            throw new IOException("HTTP exchange resulted in no result");
         } else {
            return clientResponse;
         }
      } catch (RuntimeException ex) {
         throw convertException(ex);
      }
   }

   private Publisher<Void> send(HttpHeaders headers, StreamingHttpOutputMessage.@Nullable Body body, HttpClientRequest request, NettyOutbound outbound) {
      headers.forEach((key, value) -> request.requestHeaders().set(key, value));
      return (Publisher<Void>)(body == null
         ? Mono.empty()
         : outbound.send(
            FlowAdapters.toPublisher(
               new OutputStreamPublisher<>(
                  os -> body.writeTo(StreamUtils.nonClosing(os)), new ReactorClientHttpRequest.ByteBufMapper(outbound), this.executor, null
               )
            )
         ));
   }

   static IOException convertException(RuntimeException ex) {
      Throwable cause = ex.getCause();
      if (cause instanceof IOException ioEx) {
         return ioEx;
      } else {
         if (cause instanceof UncheckedIOException uioEx) {
            IOException ioEx = uioEx.getCause();
            if (ioEx != null) {
               return ioEx;
            }
         }

         return new IOException(ex.getMessage(), cause != null ? cause : ex);
      }
   }

   private static final class ByteBufMapper implements OutputStreamPublisher.ByteMapper<ByteBuf> {
      private final ByteBufAllocator allocator;

      public ByteBufMapper(NettyOutbound outbound) {
         this.allocator = outbound.alloc();
      }

      public ByteBuf map(int b) {
         ByteBuf buf = this.allocator.buffer(1);
         buf.writeByte(b);
         return buf;
      }

      public ByteBuf map(byte[] b, int off, int len) {
         ByteBuf buf = this.allocator.buffer(len);
         buf.writeBytes(b, off, len);
         return buf;
      }
   }
}
