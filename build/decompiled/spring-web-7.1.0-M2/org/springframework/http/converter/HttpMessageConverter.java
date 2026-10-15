package org.springframework.http.converter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;

public interface HttpMessageConverter<T> {
   boolean canRead(Class<?> clazz, @Nullable MediaType mediaType);

   boolean canWrite(Class<?> clazz, @Nullable MediaType mediaType);

   default boolean canWriteRepeatedly(T t, @Nullable MediaType contentType) {
      return false;
   }

   List<MediaType> getSupportedMediaTypes();

   default List<MediaType> getSupportedMediaTypes(Class<?> clazz) {
      return !this.canRead(clazz, null) && !this.canWrite(clazz, null) ? Collections.emptyList() : this.getSupportedMediaTypes();
   }

   T read(Class<? extends T> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException;

   void write(T t, @Nullable MediaType contentType, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException;
}
