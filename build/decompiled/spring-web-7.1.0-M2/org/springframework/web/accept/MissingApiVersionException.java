package org.springframework.web.accept;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class MissingApiVersionException extends ResponseStatusException {
   public MissingApiVersionException() {
      super(HttpStatus.BAD_REQUEST, "API version is required.");
   }
}
