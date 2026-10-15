package org.springframework.boot.context.properties.bind.validation;

import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public class BindValidationException extends RuntimeException {
   private final ValidationErrors validationErrors;

   BindValidationException(ValidationErrors validationErrors) {
      super(getMessage(validationErrors));
      Assert.notNull(validationErrors, "'validationErrors' must not be null");
      this.validationErrors = validationErrors;
   }

   public ValidationErrors getValidationErrors() {
      return this.validationErrors;
   }

   private static String getMessage(@Nullable ValidationErrors errors) {
      StringBuilder message = new StringBuilder("Binding validation errors");
      if (errors != null) {
         message.append(" on ").append(errors.getName());
         errors.getAllErrors().forEach(error -> message.append(String.format("%n   - %s", error)));
      }

      return message.toString();
   }
}
