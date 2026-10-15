package org.springframework.http.server.reactive;

import java.util.concurrent.atomic.AtomicBoolean;
import org.reactivestreams.Processor;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpHeaders;
import reactor.core.publisher.Mono;

public abstract class AbstractListenerServerHttpResponse extends AbstractServerHttpResponse {
   private final AtomicBoolean writeCalled = new AtomicBoolean();

   public AbstractListenerServerHttpResponse(DataBufferFactory bufferFactory) {
      super(bufferFactory);
   }

   public AbstractListenerServerHttpResponse(DataBufferFactory bufferFactory, HttpHeaders headers) {
      super(bufferFactory, headers);
   }

   @Override
   protected final Mono<Void> writeWithInternal(Publisher<? extends DataBuffer> body) {
      return this.writeAndFlushWithInternal(Mono.just(body));
   }

   @Override
   protected final Mono<Void> writeAndFlushWithInternal(Publisher<? extends Publisher<? extends DataBuffer>> body) {
      if (!this.writeCalled.compareAndSet(false, true)) {
         return Mono.error(new IllegalStateException("writeWith() or writeAndFlushWith() has already been called"));
      }

      Processor<? super Publisher<? extends DataBuffer>, Void> processor = this.createBodyFlushProcessor();
      return Mono.from(subscriber -> {
         body.subscribe(processor);
         processor.subscribe(subscriber);
      });
   }

   protected abstract Processor<? super Publisher<? extends DataBuffer>, Void> createBodyFlushProcessor();
}
