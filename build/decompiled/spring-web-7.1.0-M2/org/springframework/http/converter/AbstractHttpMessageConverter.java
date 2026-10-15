package org.springframework.http.converter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpLogging;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.StreamingHttpOutputMessage;
import org.springframework.util.Assert;

public abstract class AbstractHttpMessageConverter<T> implements HttpMessageConverter<T> {
   protected final Log logger = HttpLogging.forLogName(this.getClass());
   private List<MediaType> supportedMediaTypes = Collections.emptyList();
   private @Nullable Charset defaultCharset;

   protected AbstractHttpMessageConverter() {
   }

   protected AbstractHttpMessageConverter(MediaType supportedMediaType) {
      this.setSupportedMediaTypes(Collections.singletonList(supportedMediaType));
   }

   protected AbstractHttpMessageConverter(MediaType... supportedMediaTypes) {
      this.setSupportedMediaTypes(Arrays.asList(supportedMediaTypes));
   }

   protected AbstractHttpMessageConverter(Charset defaultCharset, MediaType... supportedMediaTypes) {
      this.defaultCharset = defaultCharset;
      this.setSupportedMediaTypes(Arrays.asList(supportedMediaTypes));
   }

   public void setSupportedMediaTypes(List<MediaType> supportedMediaTypes) {
      Assert.notEmpty(supportedMediaTypes, "MediaType List must not be empty");
      this.supportedMediaTypes = List.copyOf(supportedMediaTypes);
   }

   @Override
   public List<MediaType> getSupportedMediaTypes() {
      return this.supportedMediaTypes;
   }

   public void setDefaultCharset(@Nullable Charset defaultCharset) {
      this.defaultCharset = defaultCharset;
   }

   public @Nullable Charset getDefaultCharset() {
      return this.defaultCharset;
   }

   @Override
   public boolean canRead(Class<?> clazz, @Nullable MediaType mediaType) {
      return this.supports(clazz) && this.canRead(mediaType);
   }

   protected boolean canRead(@Nullable MediaType mediaType) {
      if (mediaType == null) {
         return true;
      }

      for (MediaType supportedMediaType : this.getSupportedMediaTypes()) {
         if (supportedMediaType.includes(mediaType)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public boolean canWrite(Class<?> clazz, @Nullable MediaType mediaType) {
      return this.supports(clazz) && this.canWrite(mediaType);
   }

   protected boolean canWrite(@Nullable MediaType mediaType) {
      if (mediaType != null && !MediaType.ALL.equalsTypeAndSubtype(mediaType)) {
         for (MediaType supportedMediaType : this.getSupportedMediaTypes()) {
            if (supportedMediaType.isCompatibleWith(mediaType)) {
               return true;
            }
         }

         return false;
      } else {
         return true;
      }
   }

   @Override
   public final T read(Class<? extends T> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
      return this.readInternal(clazz, inputMessage);
   }

   @Override
   public final void write(final T t, @Nullable MediaType contentType, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
      final HttpHeaders headers = outputMessage.getHeaders();
      this.addDefaultHeaders(headers, t, contentType);
      if (outputMessage instanceof StreamingHttpOutputMessage streamingOutputMessage) {
         streamingOutputMessage.setBody(new StreamingHttpOutputMessage.Body() {
            @Override
            public void writeTo(OutputStream outputStream) throws IOException {
               AbstractHttpMessageConverter.this.writeInternal(t, new HttpOutputMessage() {
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
               return AbstractHttpMessageConverter.this.canWriteRepeatedly(t, null);
            }
         });
      } else {
         this.writeInternal(t, outputMessage);
         outputMessage.getBody().flush();
      }
   }

   protected void addDefaultHeaders(HttpHeaders headers, T t, @Nullable MediaType contentType) throws IOException {
      if (headers.getContentType() == null) {
         MediaType contentTypeToUse = contentType;
         if (contentType == null || !contentType.isConcrete()) {
            contentTypeToUse = this.getDefaultContentType(t);
         } else if (MediaType.APPLICATION_OCTET_STREAM.equals(contentType)) {
            MediaType mediaType = this.getDefaultContentType(t);
            contentTypeToUse = mediaType != null ? mediaType : contentTypeToUse;
         }

         if (contentTypeToUse != null) {
            String value = contentTypeToUse.toString();
            if (contentTypeToUse.getCharset() == null) {
               Charset defaultCharset = this.getDefaultCharset();
               if (defaultCharset != null) {
                  value = value + ";charset=" + defaultCharset.name();
               }
            }

            headers.set("Content-Type", value);
         }
      }

      if (headers.getContentLength() < 0L && !headers.containsHeader("Transfer-Encoding")) {
         Long contentLength = this.getContentLength(t, headers.getContentType());
         if (contentLength != null) {
            headers.setContentLength(contentLength);
         }
      }
   }

   protected @Nullable MediaType getDefaultContentType(T t) throws IOException {
      List<MediaType> mediaTypes = this.getSupportedMediaTypes();
      return !mediaTypes.isEmpty() ? mediaTypes.get(0) : null;
   }

   protected @Nullable Long getContentLength(T t, @Nullable MediaType contentType) throws IOException {
      return null;
   }

   @Deprecated(since = "7.1", forRemoval = true)
   protected boolean supportsRepeatableWrites(T t) {
      return this.canWriteRepeatedly(t, null);
   }

   protected abstract boolean supports(Class<?> clazz);

   protected abstract T readInternal(Class<? extends T> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException;

   protected abstract void writeInternal(T t, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException;
}
