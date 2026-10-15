package org.springframework.aot.generate;

import org.jspecify.annotations.Nullable;

public class ValueCodeGenerationException extends RuntimeException {
   private final @Nullable Object value;

   protected ValueCodeGenerationException(String message, @Nullable Object value, @Nullable Throwable cause) {
      super(message, cause);
      this.value = value;
   }

   public ValueCodeGenerationException(@Nullable Object value, Throwable cause) {
      super(buildErrorMessage(value), cause);
      this.value = value;
   }

   private static String buildErrorMessage(@Nullable Object value) {
      StringBuilder message = new StringBuilder("Failed to generate code for '");
      message.append(value).append("'");
      if (value != null) {
         message.append(" with type ").append(value.getClass());
      }

      return message.toString();
   }

   public @Nullable Object getValue() {
      return this.value;
   }
}
