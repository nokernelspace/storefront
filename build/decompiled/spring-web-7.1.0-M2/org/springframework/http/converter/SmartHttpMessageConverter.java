package org.springframework.http.converter;

import java.io.IOException;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;

public interface SmartHttpMessageConverter<T> extends HttpMessageConverter<T> {
   boolean canRead(ResolvableType type, @Nullable MediaType mediaType);

   @Override
   default boolean canRead(Class<?> clazz, @Nullable MediaType mediaType) {
      return this.canRead(ResolvableType.forClass(clazz), mediaType);
   }

   T read(ResolvableType type, HttpInputMessage inputMessage, @Nullable Map<String, Object> hints) throws IOException, HttpMessageNotReadableException;

   @Override
   default T read(Class<? extends T> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
      return this.read(ResolvableType.forClass(clazz), inputMessage, null);
   }

   boolean canWrite(ResolvableType targetType, Class<?> valueClass, @Nullable MediaType mediaType);

   @Override
   default boolean canWrite(Class<?> clazz, @Nullable MediaType mediaType) {
      return this.canWrite(ResolvableType.forClass(clazz), clazz, mediaType);
   }

   void write(T t, ResolvableType type, @Nullable MediaType contentType, HttpOutputMessage outputMessage, @Nullable Map<String, Object> hints) throws IOException, HttpMessageNotWritableException;

   @Override
   default void write(T t, @Nullable MediaType contentType, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
      this.write(t, ResolvableType.forInstance(t), contentType, outputMessage, null);
   }
}
