package org.springframework.http.converter;

import java.io.IOException;
import java.util.function.Predicate;
import kotlinx.serialization.BinaryFormat;
import kotlinx.serialization.KSerializer;
import kotlinx.serialization.SerializationException;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.util.StreamUtils;

public abstract class KotlinSerializationBinaryHttpMessageConverter<T extends BinaryFormat> extends AbstractKotlinSerializationHttpMessageConverter<T> {
   protected KotlinSerializationBinaryHttpMessageConverter(T format, MediaType... supportedMediaTypes) {
      super(format, supportedMediaTypes);
   }

   protected KotlinSerializationBinaryHttpMessageConverter(T format, Predicate<ResolvableType> typePredicate, MediaType... supportedMediaTypes) {
      super(format, typePredicate, supportedMediaTypes);
   }

   protected Object readInternal(KSerializer<Object> serializer, T format, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
      byte[] bytes = StreamUtils.copyToByteArray(inputMessage.getBody());

      try {
         return format.decodeFromByteArray(serializer, bytes);
      } catch (SerializationException ex) {
         throw new HttpMessageNotReadableException("Could not read " + format + ": " + ex.getMessage(), ex, inputMessage);
      }
   }

   protected void writeInternal(Object object, KSerializer<Object> serializer, T format, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
      try {
         byte[] bytes = format.encodeToByteArray(serializer, object);
         outputMessage.getBody().write(bytes);
      } catch (SerializationException ex) {
         throw new HttpMessageNotWritableException("Could not write " + format + ": " + ex.getMessage(), ex);
      }
   }
}
