package org.springframework.http.server.reactive;

import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class HttpHeadResponseDecorator extends ServerHttpResponseDecorator {
   public HttpHeadResponseDecorator(ServerHttpResponse delegate) {
      super(delegate);
   }

   @Override
   public final Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
      return this.shouldSetContentLength() && body instanceof Mono ? ((Mono)body).doOnSuccess(buffer -> {
         if (buffer != null) {
            this.getHeaders().setContentLength(buffer.readableByteCount());
            DataBufferUtils.release(buffer);
         } else {
            this.getHeaders().setContentLength(0L);
         }
      }).then() : Flux.from(body).doOnNext(DataBufferUtils::release).then();
   }

   private boolean shouldSetContentLength() {
      return this.getHeaders().getFirst("Content-Length") == null && this.getHeaders().getFirst("Transfer-Encoding") == null;
   }

   @Override
   public final Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
      return this.setComplete();
   }
}
