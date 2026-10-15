package org.springframework.expression.spel;

import org.jspecify.annotations.Nullable;

public class InternalParseException extends RuntimeException {
   public InternalParseException(@Nullable SpelParseException cause) {
      super(cause);
   }

   public @Nullable SpelParseException getCause() {
      return (SpelParseException)super.getCause();
   }
}
