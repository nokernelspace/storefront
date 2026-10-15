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

public class ByteArrayEncoder extends AbstractEncoder<byte[]> {
   public ByteArrayEncoder() {
      super(MimeTypeUtils.ALL);
   }

   @Override
   public boolean canEncode(ResolvableType elementType, @Nullable MimeType mimeType) {
      Class<?> clazz = elementType.toClass();
      return super.canEncode(elementType, mimeType) && byte[].class.isAssignableFrom(clazz);
   }

   @Override
   public Flux<DataBuffer> encode(
      Publisher<byte[]> inputStream,
      DataBufferFactory bufferFactory,
      ResolvableType elementType,
      @Nullable MimeType mimeType,
      @Nullable Map<String, Object> hints
   ) {
      return Flux.from(inputStream).map(bytes -> this.encodeValue(bytes, bufferFactory, elementType, mimeType, hints));
   }

   public DataBuffer encodeValue(
      byte[] bytes, DataBufferFactory bufferFactory, ResolvableType valueType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints
   ) {
      DataBuffer dataBuffer = bufferFactory.wrap(bytes);
      if (this.logger.isDebugEnabled() && !Hints.isLoggingSuppressed(hints)) {
         String logPrefix = Hints.getLogPrefix(hints);
         this.logger.debug(logPrefix + "Writing " + dataBuffer.readableByteCount() + " bytes");
      }

      return dataBuffer;
   }
}
