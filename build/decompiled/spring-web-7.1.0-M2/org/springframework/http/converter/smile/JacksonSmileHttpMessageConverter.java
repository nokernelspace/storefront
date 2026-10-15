package org.springframework.http.converter.smile;

import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractJacksonHttpMessageConverter;
import tools.jackson.dataformat.smile.SmileMapper;
import tools.jackson.dataformat.smile.SmileMapper.Builder;

public class JacksonSmileHttpMessageConverter extends AbstractJacksonHttpMessageConverter<SmileMapper> {
   private static final MediaType DEFAULT_SMILE_MIME_TYPES = new MediaType("application", "x-jackson-smile");

   public JacksonSmileHttpMessageConverter() {
      super(SmileMapper.builder(), DEFAULT_SMILE_MIME_TYPES);
   }

   public JacksonSmileHttpMessageConverter(Builder builder) {
      super(builder, DEFAULT_SMILE_MIME_TYPES);
   }

   public JacksonSmileHttpMessageConverter(SmileMapper mapper) {
      super(mapper, DEFAULT_SMILE_MIME_TYPES);
   }
}
