package org.springframework.http.converter;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.StreamingHttpOutputMessage;

public abstract class AbstractSmartHttpMessageConverter<T> extends AbstractHttpMessageConverter<T> implements SmartHttpMessageConverter<T> {
   protected AbstractSmartHttpMessageConverter() {
   }

   protected AbstractSmartHttpMessageConverter(MediaType supportedMediaType) {
      super(supportedMediaType);
   }

   protected AbstractSmartHttpMessageConverter(MediaType... supportedMediaTypes) {
      super(supportedMediaTypes);
   }

   @Override
   protected boolean supports(Class<?> clazz) {
      return true;
   }

   @Override
   public boolean canRead(Class<?> clazz, @Nullable MediaType mediaType) {
      return this.canRead(ResolvableType.forClass(clazz), mediaType);
   }

   @Override
   public boolean canWrite(Class<?> clazz, @Nullable MediaType mediaType) {
      return this.canWrite(ResolvableType.forClass(clazz), clazz, mediaType);
   }

   @Override
   public final void write(T t, ResolvableType type, @Nullable MediaType contentType, HttpOutputMessage outputMessage, @Nullable Map<String, Object> hints) throws IOException, HttpMessageNotWritableException {
      final HttpHeaders headers = outputMessage.getHeaders();
      this.addDefaultHeaders(headers, t, contentType);
      if (outputMessage instanceof StreamingHttpOutputMessage streamingOutputMessage) {
         streamingOutputMessage.setBody(new StreamingHttpOutputMessage.Body() {
            @Override
            public void writeTo(OutputStream outputStream) throws IOException {
               AbstractSmartHttpMessageConverter.this.writeInternal(t, type, new HttpOutputMessage() {
                  @Override
                  public OutputStream getBody() {
                     return outputStream;
                  }

                  @Override
                  public HttpHeaders getHeaders() {
                     return headers;
                  }
               }, hints);
            }

            @Override
            public boolean repeatable() {
               return AbstractSmartHttpMessageConverter.this.canWriteRepeatedly(t, contentType);
            }
         });
      } else {
         this.writeInternal(t, type, outputMessage, hints);
         outputMessage.getBody().flush();
      }
   }

   @Override
   protected void writeInternal(T t, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
      this.writeInternal(t, ResolvableType.NONE, outputMessage, null);
   }

   protected abstract void writeInternal(T t, ResolvableType type, HttpOutputMessage outputMessage, @Nullable Map<String, Object> hints) throws IOException, HttpMessageNotWritableException;

   @Override
   protected T readInternal(Class<? extends T> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
      return this.read(ResolvableType.forClass(clazz), inputMessage, null);
   }
}
