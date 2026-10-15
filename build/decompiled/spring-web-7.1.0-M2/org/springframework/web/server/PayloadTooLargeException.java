package org.springframework.web.server;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

@Deprecated(since = "7.0")
public class PayloadTooLargeException extends ResponseStatusException {
   public PayloadTooLargeException(@Nullable Throwable cause) {
      super(HttpStatus.PAYLOAD_TOO_LARGE, null, cause);
   }
}
