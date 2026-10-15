package org.springframework.http.codec.cbor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.AbstractJackson2Encoder;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;

@Deprecated(since = "7.0", forRemoval = true)
public class Jackson2CborEncoder extends AbstractJackson2Encoder {
   public Jackson2CborEncoder() {
      this(Jackson2ObjectMapperBuilder.cbor().build(), MediaType.APPLICATION_CBOR);
   }

   public Jackson2CborEncoder(ObjectMapper mapper, MimeType... mimeTypes) {
      super(mapper, mimeTypes);
      Assert.isAssignable(CBORFactory.class, mapper.getFactory().getClass());
   }

   @Override
   public Flux<DataBuffer> encode(
      Publisher<?> inputStream, DataBufferFactory bufferFactory, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints
   ) {
      throw new UnsupportedOperationException("Does not support stream encoding yet");
   }
}
