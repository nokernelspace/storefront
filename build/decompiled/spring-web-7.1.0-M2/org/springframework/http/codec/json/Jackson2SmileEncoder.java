package org.springframework.http.codec.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.smile.SmileFactory;
import java.util.Collections;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;

@Deprecated(since = "7.0", forRemoval = true)
public class Jackson2SmileEncoder extends AbstractJackson2Encoder {
   private static final MimeType[] DEFAULT_SMILE_MIME_TYPES = new MimeType[]{
      new MimeType("application", "x-jackson-smile"), new MimeType("application", "*+x-jackson-smile")
   };
   private static final byte[] STREAM_SEPARATOR = new byte[0];

   public Jackson2SmileEncoder() {
      this(Jackson2ObjectMapperBuilder.smile().build(), DEFAULT_SMILE_MIME_TYPES);
   }

   public Jackson2SmileEncoder(ObjectMapper mapper, MimeType... mimeTypes) {
      super(mapper, mimeTypes);
      Assert.isAssignable(SmileFactory.class, mapper.getFactory().getClass());
      this.setStreamingMediaTypes(Collections.singletonList(new MediaType("application", "stream+x-jackson-smile")));
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
