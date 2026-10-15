package org.springframework.web.context.request.async;

import java.io.IOException;

public class AsyncRequestNotUsableException extends IOException {
   public AsyncRequestNotUsableException(String message) {
      super(message);
   }

   public AsyncRequestNotUsableException(String message, Throwable cause) {
      super(message, cause);
   }
}
