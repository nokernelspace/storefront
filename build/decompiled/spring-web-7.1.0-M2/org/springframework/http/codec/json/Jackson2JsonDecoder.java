package org.springframework.http.codec.json;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;

@Deprecated(since = "7.0", forRemoval = true)
public class Jackson2JsonDecoder extends AbstractJackson2Decoder {
   private static final CharBufferDecoder CHAR_BUFFER_DECODER = CharBufferDecoder.textPlainOnly(Arrays.asList(",", "\n"), false);
   private static final ResolvableType CHAR_BUFFER_TYPE = ResolvableType.forClass(CharBuffer.class);

   public Jackson2JsonDecoder() {
      super(Jackson2ObjectMapperBuilder.json().build());
   }

   public Jackson2JsonDecoder(ObjectMapper mapper, MimeType... mimeTypes) {
      super(mapper, mimeTypes);
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
