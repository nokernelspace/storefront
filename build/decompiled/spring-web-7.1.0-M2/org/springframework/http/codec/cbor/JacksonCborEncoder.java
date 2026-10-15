package org.springframework.http.codec.cbor;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.AbstractJacksonEncoder;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;
import tools.jackson.dataformat.cbor.CBORMapper;
import tools.jackson.dataformat.cbor.CBORMapper.Builder;

public class JacksonCborEncoder extends AbstractJacksonEncoder<CBORMapper> {
   public JacksonCborEncoder() {
      super(CBORMapper.builder(), MediaType.APPLICATION_CBOR);
   }

   public JacksonCborEncoder(Builder builder) {
      super(builder, MediaType.APPLICATION_CBOR);
   }

   public JacksonCborEncoder(CBORMapper mapper) {
      super(mapper, MediaType.APPLICATION_CBOR);
   }

   public JacksonCborEncoder(Builder builder, MimeType... mimeTypes) {
      super(builder, mimeTypes);
   }

   public JacksonCborEncoder(CBORMapper mapper, MimeType... mimeTypes) {
      super(mapper, mimeTypes);
   }

   @Override
   public Flux<DataBuffer> encode(
      Publisher<?> inputStream, DataBufferFactory bufferFactory, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints
   ) {
      throw new UnsupportedOperationException("Stream encoding is currently not supported");
   }
}
