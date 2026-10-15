package org.springframework.http.codec.smile;

import org.springframework.http.codec.AbstractJacksonDecoder;
import org.springframework.util.MimeType;
import tools.jackson.dataformat.smile.SmileMapper;
import tools.jackson.dataformat.smile.SmileMapper.Builder;

public class JacksonSmileDecoder extends AbstractJacksonDecoder<SmileMapper> {
   private static final MimeType[] DEFAULT_SMILE_MIME_TYPES = new MimeType[]{
      new MimeType("application", "x-jackson-smile"), new MimeType("application", "*+x-jackson-smile")
   };

   public JacksonSmileDecoder() {
      super(SmileMapper.builder(), DEFAULT_SMILE_MIME_TYPES);
   }

   public JacksonSmileDecoder(Builder builder) {
      this(builder, DEFAULT_SMILE_MIME_TYPES);
   }

   public JacksonSmileDecoder(SmileMapper mapper) {
      this(mapper, DEFAULT_SMILE_MIME_TYPES);
   }

   public JacksonSmileDecoder(Builder builder, MimeType... mimeTypes) {
      super(builder, mimeTypes);
   }

   public JacksonSmileDecoder(SmileMapper mapper, MimeType... mimeTypes) {
      super(mapper, mimeTypes);
   }
}
