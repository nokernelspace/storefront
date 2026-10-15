package org.springframework.web.server;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;

public class ContentTooLargeException extends ResponseStatusException {
   public ContentTooLargeException(@Nullable Throwable cause) {
      super(HttpStatus.CONTENT_TOO_LARGE, null, cause);
   }
}
