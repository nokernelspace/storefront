package org.springframework.web.accept;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class InvalidApiVersionException extends ResponseStatusException {
   private final String version;

   public InvalidApiVersionException(String version) {
      this(version, null, null);
   }

   public InvalidApiVersionException(String version, @Nullable String msg, @Nullable Exception cause) {
      super(HttpStatus.BAD_REQUEST, msg != null ? msg : "Invalid API version: '" + version + "'.", cause);
      this.version = version;
   }

   public String getVersion() {
      return this.version;
   }
}
