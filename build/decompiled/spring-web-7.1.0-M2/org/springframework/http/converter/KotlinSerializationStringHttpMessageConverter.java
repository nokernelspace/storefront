package org.springframework.http.converter;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.function.Predicate;
import kotlinx.serialization.KSerializer;
import kotlinx.serialization.SerializationException;
import kotlinx.serialization.StringFormat;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;

public abstract class KotlinSerializationStringHttpMessageConverter<T extends StringFormat> extends AbstractKotlinSerializationHttpMessageConverter<T> {
   protected KotlinSerializationStringHttpMessageConverter(T format, MediaType... supportedMediaTypes) {
      super(format, supportedMediaTypes);
   }

   protected KotlinSerializationStringHttpMessageConverter(T format, Predicate<ResolvableType> typePredicate, MediaType... supportedMediaTypes) {
      super(format, typePredicate, supportedMediaTypes);
   }

   protected Object readInternal(KSerializer<Object> serializer, T format, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
      Charset charset = charset(inputMessage.getHeaders().getContentType());
      String s = StreamUtils.copyToString(inputMessage.getBody(), charset);

      try {
         return format.decodeFromString(serializer, s);
      } catch (SerializationException ex) {
         throw new HttpMessageNotReadableException("Could not read " + format + ": " + ex.getMessage(), ex, inputMessage);
      }
   }

   protected void writeInternal(Object object, KSerializer<Object> serializer, T format, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
      try {
         String s = format.encodeToString(serializer, object);
         Charset charset = charset(outputMessage.getHeaders().getContentType());
         outputMessage.getBody().write(s.getBytes(charset));
      } catch (SerializationException ex) {
         throw new HttpMessageNotWritableException("Could not write " + format + ": " + ex.getMessage(), ex);
      }
   }

   private static Charset charset(@Nullable MediaType contentType) {
      return contentType != null && contentType.getCharset() != null ? contentType.getCharset() : StandardCharsets.UTF_8;
   }
}
