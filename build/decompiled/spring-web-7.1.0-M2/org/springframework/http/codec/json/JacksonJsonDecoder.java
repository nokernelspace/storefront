package org.springframework.http.codec.json;

import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.CharBufferDecoder;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.AbstractJacksonDecoder;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.json.JsonMapper.Builder;

public class JacksonJsonDecoder extends AbstractJacksonDecoder<JsonMapper> {
   private static final CharBufferDecoder CHAR_BUFFER_DECODER = CharBufferDecoder.textPlainOnly(Arrays.asList(",", "\n"), false);
   private static final ResolvableType CHAR_BUFFER_TYPE = ResolvableType.forClass(CharBuffer.class);
   private static final MimeType[] DEFAULT_JSON_MIME_TYPES = new MimeType[]{
      MediaType.APPLICATION_JSON, new MediaType("application", "*+json"), MediaType.APPLICATION_NDJSON, MediaType.APPLICATION_JSONL
   };

   public JacksonJsonDecoder() {
      super(JsonMapper.builder(), DEFAULT_JSON_MIME_TYPES);
   }

   public JacksonJsonDecoder(Builder builder) {
      super(builder, DEFAULT_JSON_MIME_TYPES);
   }

   public JacksonJsonDecoder(JsonMapper mapper) {
      super(mapper, DEFAULT_JSON_MIME_TYPES);
   }

   public JacksonJsonDecoder(Builder builder, MimeType... mimeTypes) {
      super(builder, mimeTypes);
   }

   public JacksonJsonDecoder(JsonMapper mapper, MimeType... mimeTypes) {
      super(mapper, mimeTypes);
   }

   @Override
   public boolean canDecode(ResolvableType elementType, @Nullable MimeType mimeType) {
      return super.canDecode(elementType, mimeType) && String.class != elementType.toClass();
   }

   @Override
   protected Flux<DataBuffer> processInput(
      Publisher<DataBuffer> input, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints
   ) {
      Flux<DataBuffer> flux = Flux.from(input);
      if (mimeType == null) {
         return flux;
      } else {
         Charset charset = mimeType.getCharset();
         if (charset != null && !StandardCharsets.UTF_8.equals(charset) && !StandardCharsets.US_ASCII.equals(charset)) {
            MimeType textMimeType = new MimeType(MimeTypeUtils.TEXT_PLAIN, charset);
            Flux<CharBuffer> decoded = CHAR_BUFFER_DECODER.decode(input, CHAR_BUFFER_TYPE, textMimeType, null);
            return decoded.map(charBuffer -> DefaultDataBufferFactory.sharedInstance.wrap(StandardCharsets.UTF_8.encode(charBuffer)));
         } else {
            return flux;
         }
      }
   }
}
