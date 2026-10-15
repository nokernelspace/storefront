package org.springframework.http.codec.cbor;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.codec.AbstractJacksonDecoder;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;
import tools.jackson.dataformat.cbor.CBORMapper;
import tools.jackson.dataformat.cbor.CBORMapper.Builder;

public class JacksonCborDecoder extends AbstractJacksonDecoder<CBORMapper> {
   public JacksonCborDecoder() {
      super(CBORMapper.builder(), MediaType.APPLICATION_CBOR);
   }

   public JacksonCborDecoder(Builder builder) {
      super(builder, MediaType.APPLICATION_CBOR);
   }

   public JacksonCborDecoder(CBORMapper mapper) {
      super(mapper, MediaType.APPLICATION_CBOR);
   }

   public JacksonCborDecoder(Builder builder, MimeType... mimeTypes) {
      super(builder, mimeTypes);
   }

   public JacksonCborDecoder(CBORMapper mapper, MimeType... mimeTypes) {
      super(mapper, mimeTypes);
   }

   @Override
   public Flux<Object> decode(Publisher<DataBuffer> input, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {
      throw new UnsupportedOperationException("Stream decoding is currently not supported");
   }
}
