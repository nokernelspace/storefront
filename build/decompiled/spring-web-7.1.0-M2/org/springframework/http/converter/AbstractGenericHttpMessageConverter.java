package org.springframework.http.converter;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.StreamingHttpOutputMessage;

public abstract class AbstractGenericHttpMessageConverter<T> extends AbstractHttpMessageConverter<T> implements GenericHttpMessageConverter<T> {
   protected AbstractGenericHttpMessageConverter() {
   }

   protected AbstractGenericHttpMessageConverter(MediaType supportedMediaType) {
      super(supportedMediaType);
   }

   protected AbstractGenericHttpMessageConverter(MediaType... supportedMediaTypes) {
      super(supportedMediaTypes);
   }

   protected AbstractGenericHttpMessageConverter(Charset defaultCharset, MediaType... supportedMediaTypes) {
      super(defaultCharset, supportedMediaTypes);
   }

   @Override
   protected boolean supports(Class<?> clazz) {
      return true;
   }

   @Override
   public boolean canRead(Type type, @Nullable Class<?> contextClass, @Nullable MediaType mediaType) {
      return type instanceof Class<?> clazz ? this.canRead(clazz, mediaType) : this.canRead(mediaType);
   }

   @Override
   public boolean canWrite(@Nullable Type type, Class<?> clazz, @Nullable MediaType mediaType) {
      return this.canWrite(clazz, mediaType);
   }

   @Override
   public final void write(final T t, final @Nullable Type type, @Nullable MediaType contentType, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
      final HttpHeaders headers = outputMessage.getHeaders();
      this.addDefaultHeaders(headers, t, contentType);
      if (outputMessage instanceof StreamingHttpOutputMessage streamingOutputMessage) {
         streamingOutputMessage.setBody(new StreamingHttpOutputMessage.Body() {
            @Override
            public void writeTo(OutputStream outputStream) throws IOException {
               AbstractGenericHttpMessageConverter.this.writeInternal(t, type, new HttpOutputMessage() {
                  @Override
                  public OutputStream getBody() {
                     return outputStream;
                  }

                  @Override
                  public HttpHeaders getHeaders() {
                     return headers;
                  }
               });
            }

            @Override
            public boolean repeatable() {
               return AbstractGenericHttpMessageConverter.this.canWriteRepeatedly(t, contentType);
            }
         });
      } else {
         this.writeInternal(t, type, outputMessage);
         outputMessage.getBody().flush();
      }
   }

   @Override
   protected void writeInternal(T t, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
      this.writeInternal(t, null, outputMessage);
   }

   protected abstract void writeInternal(T t, @Nullable Type type, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException;
}
