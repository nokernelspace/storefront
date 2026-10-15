package org.springframework.http.codec.smile;

import java.util.Collections;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.codec.AbstractJacksonEncoder;
import org.springframework.util.MimeType;
import tools.jackson.dataformat.smile.SmileMapper;
import tools.jackson.dataformat.smile.SmileMapper.Builder;

public class JacksonSmileEncoder extends AbstractJacksonEncoder<SmileMapper> {
   private static final MimeType[] DEFAULT_SMILE_MIME_TYPES = new MimeType[]{
      new MimeType("application", "x-jackson-smile"), new MimeType("application", "*+x-jackson-smile")
   };
   private static final MediaType DEFAULT_SMILE_STREAMING_MEDIA_TYPE = new MediaType("application", "stream+x-jackson-smile");
   private static final byte[] STREAM_SEPARATOR = new byte[0];

   public JacksonSmileEncoder() {
      this(SmileMapper.builder(), DEFAULT_SMILE_MIME_TYPES);
   }

   public JacksonSmileEncoder(Builder builder) {
      this(builder, DEFAULT_SMILE_MIME_TYPES);
   }

   public JacksonSmileEncoder(SmileMapper mapper) {
      this(mapper, DEFAULT_SMILE_MIME_TYPES);
   }

   public JacksonSmileEncoder(Builder builder, MimeType... mimeTypes) {
      super(builder, mimeTypes);
      this.setStreamingMediaTypes(Collections.singletonList(DEFAULT_SMILE_STREAMING_MEDIA_TYPE));
   }

   public JacksonSmileEncoder(SmileMapper mapper, MimeType... mimeTypes) {
      super(mapper, mimeTypes);
      this.setStreamingMediaTypes(Collections.singletonList(DEFAULT_SMILE_STREAMING_MEDIA_TYPE));
   }

   @Override
   protected byte @Nullable [] getStreamingMediaTypeSeparator(@Nullable MimeType mimeType) {
      for (MediaType streamingMediaType : this.getStreamingMediaTypes()) {
         if (streamingMediaType.isCompatibleWith(mimeType)) {
            return STREAM_SEPARATOR;
         }
      }

      return null;
   }
}
