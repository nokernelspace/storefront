package org.springframework.web;

import jakarta.servlet.ServletException;
import org.jspecify.annotations.Nullable;

public class HttpSessionRequiredException extends ServletException {
   private final @Nullable String expectedAttribute;

   public HttpSessionRequiredException(String msg) {
      super(msg);
      this.expectedAttribute = null;
   }

   public HttpSessionRequiredException(String msg, String expectedAttribute) {
      super(msg);
      this.expectedAttribute = expectedAttribute;
   }

   public @Nullable String getExpectedAttribute() {
      return this.expectedAttribute;
   }
}
