package org.springframework.http.converter;

import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;

public class ByteArrayHttpMessageConverter extends AbstractHttpMessageConverter<byte[]> {
   public ByteArrayHttpMessageConverter() {
      super(MediaType.APPLICATION_OCTET_STREAM, MediaType.ALL);
   }

   @Override
   public boolean supports(Class<?> clazz) {
      return byte[].class == clazz;
   }

   public byte[] readInternal(Class<byte[]> clazz, HttpInputMessage message) throws IOException {
      long length = message.getHeaders().getContentLength();
      return length >= 0L && length < 2147483647L ? message.getBody().readNBytes((int)length) : message.getBody().readAllBytes();
   }

   protected Long getContentLength(byte[] bytes, @Nullable MediaType contentType) {
      return (long)bytes.length;
   }

   protected void writeInternal(byte[] bytes, HttpOutputMessage outputMessage) throws IOException {
      outputMessage.getBody().write(bytes);
   }

   public boolean canWriteRepeatedly(byte[] bytes, @Nullable MediaType contentType) {
      return true;
   }

   protected boolean supportsRepeatableWrites(byte[] bytes) {
      return true;
   }
}
