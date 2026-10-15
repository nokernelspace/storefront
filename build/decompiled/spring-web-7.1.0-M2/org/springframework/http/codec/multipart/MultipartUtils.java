package org.springframework.http.codec.multipart;

import java.io.IOException;
import java.nio.channels.Channel;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMessage;
import org.springframework.http.MediaType;

abstract class MultipartUtils {
   public static Charset charset(HttpHeaders headers) {
      MediaType contentType = headers.getContentType();
      if (contentType != null) {
         Charset charset = contentType.getCharset();
         if (charset != null) {
            return charset;
         }
      }

      return StandardCharsets.UTF_8;
   }

   public static byte @Nullable [] boundary(HttpMessage message, Charset headersCharset) {
      MediaType contentType = message.getHeaders().getContentType();
      if (contentType != null) {
         String boundary = contentType.getParameter("boundary");
         if (boundary != null) {
            int len = boundary.length();
            if (len > 2 && boundary.charAt(0) == '"' && boundary.charAt(len - 1) == '"') {
               boundary = boundary.substring(1, len - 1);
            }

            return boundary.getBytes(headersCharset);
         }
      }

      return null;
   }

   public static byte[] concat(byte[]... byteArrays) {
      int len = 0;

      for (byte[] byteArray : byteArrays) {
         len += byteArray.length;
      }

      byte[] result = new byte[len];
      len = 0;

      for (byte[] byteArray : byteArrays) {
         System.arraycopy(byteArray, 0, result, len, byteArray.length);
         len += byteArray.length;
      }

      return result;
   }

   public static void closeChannel(Channel channel) {
      try {
         if (channel.isOpen()) {
            channel.close();
         }
      } catch (IOException var2) {
      }
   }

   public static void deleteFile(Path file) {
      try {
         Files.delete(file);
      } catch (IOException var2) {
      }
   }

   public static boolean isFormField(HttpHeaders headers) {
      MediaType contentType = headers.getContentType();
      return (contentType == null || MediaType.TEXT_PLAIN.equalsTypeAndSubtype(contentType)) && headers.getContentDisposition().getFilename() == null;
   }
}
