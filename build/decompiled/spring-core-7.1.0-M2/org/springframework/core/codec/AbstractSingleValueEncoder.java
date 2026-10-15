package org.springframework.core.codec;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;

public abstract class AbstractSingleValueEncoder<T> extends AbstractEncoder<T> {
   public AbstractSingleValueEncoder(MimeType... supportedMimeTypes) {
      super(supportedMimeTypes);
   }

   @Override
   public final Flux<DataBuffer> encode(
      Publisher<? extends T> inputStream,
      DataBufferFactory bufferFactory,
      ResolvableType elementType,
      @Nullable MimeType mimeType,
      @Nullable Map<String, Object> hints
   ) {
      return Flux.from(inputStream)
         .take(1L)
         .concatMap(value -> this.encode((T)value, bufferFactory, elementType, mimeType, hints))
         .doOnDiscard(DataBuffer.class, DataBufferUtils::release);
   }

   protected abstract Flux<DataBuffer> encode(
      T t, DataBufferFactory dataBufferFactory, ResolvableType type, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints
   );
}
