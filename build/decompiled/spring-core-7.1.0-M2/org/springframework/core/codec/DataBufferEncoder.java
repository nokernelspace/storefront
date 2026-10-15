package org.springframework.core.codec;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;

public class DataBufferEncoder extends AbstractEncoder<DataBuffer> {
   public DataBufferEncoder() {
      super(MimeTypeUtils.ALL);
   }

   @Override
   public boolean canEncode(ResolvableType elementType, @Nullable MimeType mimeType) {
      Class<?> clazz = elementType.toClass();
      return super.canEncode(elementType, mimeType) && DataBuffer.class.isAssignableFrom(clazz);
   }

   @Override
   public Flux<DataBuffer> encode(
      Publisher<? extends DataBuffer> inputStream,
      DataBufferFactory bufferFactory,
      ResolvableType elementType,
      @Nullable MimeType mimeType,
      @Nullable Map<String, Object> hints
   ) {
      Flux<DataBuffer> flux = Flux.from(inputStream);
      if (this.logger.isDebugEnabled() && !Hints.isLoggingSuppressed(hints)) {
         flux = flux.doOnNext(buffer -> this.logValue(buffer, hints));
      }

      return flux;
   }

   public DataBuffer encodeValue(
      DataBuffer buffer, DataBufferFactory bufferFactory, ResolvableType valueType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints
   ) {
      if (this.logger.isDebugEnabled() && !Hints.isLoggingSuppressed(hints)) {
         this.logValue(buffer, hints);
      }

      return buffer;
   }

   private void logValue(DataBuffer buffer, @Nullable Map<String, Object> hints) {
      String logPrefix = Hints.getLogPrefix(hints);
      this.logger.debug(logPrefix + "Writing " + buffer.readableByteCount() + " bytes");
   }
}
