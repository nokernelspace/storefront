package org.springframework.http.converter;

import org.jspecify.annotations.Nullable;

public class HttpMessageNotWritableException extends HttpMessageConversionException {
   public HttpMessageNotWritableException(String msg) {
      super(msg);
   }

   public HttpMessageNotWritableException(String msg, @Nullable Throwable cause) {
      super(msg, cause);
   }
}
