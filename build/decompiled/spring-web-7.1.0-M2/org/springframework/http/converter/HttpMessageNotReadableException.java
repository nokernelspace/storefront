package org.springframework.http.converter;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpInputMessage;
import org.springframework.util.Assert;

public class HttpMessageNotReadableException extends HttpMessageConversionException {
   private final @Nullable HttpInputMessage httpInputMessage;

   public HttpMessageNotReadableException(String msg, HttpInputMessage httpInputMessage) {
      super(msg);
      this.httpInputMessage = httpInputMessage;
   }

   public HttpMessageNotReadableException(String msg, @Nullable Throwable cause, HttpInputMessage httpInputMessage) {
      super(msg, cause);
      this.httpInputMessage = httpInputMessage;
   }

   public HttpInputMessage getHttpInputMessage() {
      Assert.state(this.httpInputMessage != null, "No HttpInputMessage available - use non-deprecated constructors");
      return this.httpInputMessage;
   }
}
