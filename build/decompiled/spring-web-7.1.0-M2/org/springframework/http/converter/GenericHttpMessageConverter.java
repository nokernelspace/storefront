package org.springframework.http.converter;

import java.io.IOException;
import java.lang.reflect.Type;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;

public interface GenericHttpMessageConverter<T> extends HttpMessageConverter<T> {
   boolean canRead(Type type, @Nullable Class<?> contextClass, @Nullable MediaType mediaType);

   T read(Type type, @Nullable Class<?> contextClass, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException;

   boolean canWrite(@Nullable Type type, Class<?> clazz, @Nullable MediaType mediaType);

   void write(T t, @Nullable Type type, @Nullable MediaType contentType, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException;
}
