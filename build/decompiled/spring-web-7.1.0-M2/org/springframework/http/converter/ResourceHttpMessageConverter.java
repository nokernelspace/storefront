package org.springframework.http.converter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.util.StreamUtils;

public class ResourceHttpMessageConverter extends AbstractHttpMessageConverter<Resource> {
   private final boolean supportsReadStreaming;

   public ResourceHttpMessageConverter() {
      super(MediaType.ALL);
      this.supportsReadStreaming = true;
   }

   public ResourceHttpMessageConverter(boolean supportsReadStreaming) {
      super(MediaType.ALL);
      this.supportsReadStreaming = supportsReadStreaming;
   }

   @Override
   protected boolean supports(Class<?> clazz) {
      return Resource.class.isAssignableFrom(clazz);
   }

   protected Resource readInternal(Class<? extends Resource> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
      if (this.supportsReadStreaming && InputStreamResource.class == clazz) {
         return new InputStreamResource(inputMessage.getBody()) {
            public @Nullable String getFilename() {
               return inputMessage.getHeaders().getContentDisposition().getFilename();
            }

            public long contentLength() throws IOException {
               long length = inputMessage.getHeaders().getContentLength();
               return length != -1L ? length : super.contentLength();
            }
         };
      } else if (clazz.isAssignableFrom(ByteArrayResource.class)) {
         byte[] body = StreamUtils.copyToByteArray(inputMessage.getBody());
         return new ByteArrayResource(body) {
            public @Nullable String getFilename() {
               return inputMessage.getHeaders().getContentDisposition().getFilename();
            }
         };
      } else {
         throw new HttpMessageNotReadableException("Unsupported resource class: " + clazz, inputMessage);
      }
   }

   protected void writeInternal(Resource resource, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
      this.writeContent(resource, outputMessage);
   }

   public void addDefaultHeaders(HttpOutputMessage message, Resource resource, @Nullable MediaType contentType) throws IOException {
      this.addDefaultHeaders(message.getHeaders(), resource, contentType);
   }

   protected MediaType getDefaultContentType(Resource resource) {
      return MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM);
   }

   protected @Nullable Long getContentLength(Resource resource, @Nullable MediaType contentType) throws IOException {
      if (InputStreamResource.class == resource.getClass()) {
         return null;
      }

      long contentLength = resource.contentLength();
      return contentLength < 0L ? null : contentLength;
   }

   public boolean canWriteRepeatedly(Resource resource, @Nullable MediaType contentType) {
      return !(resource instanceof InputStreamResource);
   }

   protected boolean supportsRepeatableWrites(Resource resource) {
      return this.canWriteRepeatedly(resource, null);
   }

   protected void writeContent(Resource resource, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
      try {
         InputStream in = resource.getInputStream();

         try {
            OutputStream out = outputMessage.getBody();
            in.transferTo(out);
         } catch (NullPointerException var14) {
         } finally {
            try {
               in.close();
            } catch (Throwable var13) {
            }
         }
      } catch (FileNotFoundException var16) {
      }
   }
}
