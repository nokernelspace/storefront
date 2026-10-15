package org.springframework.http.converter.cbor;

import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractJacksonHttpMessageConverter;
import tools.jackson.dataformat.cbor.CBORMapper;
import tools.jackson.dataformat.cbor.CBORMapper.Builder;

public class JacksonCborHttpMessageConverter extends AbstractJacksonHttpMessageConverter<CBORMapper> {
   public JacksonCborHttpMessageConverter() {
      super(CBORMapper.builder(), MediaType.APPLICATION_CBOR);
   }

   public JacksonCborHttpMessageConverter(Builder builder) {
      super(builder, MediaType.APPLICATION_CBOR);
   }

   public JacksonCborHttpMessageConverter(CBORMapper mapper) {
      super(mapper, MediaType.APPLICATION_CBOR);
   }
}
