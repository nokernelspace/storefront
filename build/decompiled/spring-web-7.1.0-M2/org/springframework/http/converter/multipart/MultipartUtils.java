package org.springframework.http.converter.multipart;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

abstract class MultipartUtils {
   static Charset charset(HttpHeaders headers) {
      MediaType contentType = headers.getContentType();
      if (contentType != null) {
         Charset charset = contentType.getCharset();
         if (charset != null) {
            return charset;
         }
      }

      return StandardCharsets.UTF_8;
   }
}
