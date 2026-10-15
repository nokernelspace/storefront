package org.springframework.boot.context.properties.bind.validation;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginProvider;
import org.springframework.lang.Contract;
import org.springframework.validation.FieldError;

final class OriginTrackedFieldError extends FieldError implements OriginProvider {
   private final @Nullable Origin origin;

   private OriginTrackedFieldError(FieldError fieldError, @Nullable Origin origin) {
      super(
         fieldError.getObjectName(),
         fieldError.getField(),
         fieldError.getRejectedValue(),
         fieldError.isBindingFailure(),
         fieldError.getCodes(),
         fieldError.getArguments(),
         fieldError.getDefaultMessage()
      );
      this.origin = origin;
   }

   @Override
   public @Nullable Origin getOrigin() {
      return this.origin;
   }

   @Override
   public String toString() {
      return this.origin == null ? super.toString() : super.toString() + "; origin " + this.origin;
   }

   @Contract("!null, _ -> !null")
   static @Nullable FieldError of(@Nullable FieldError fieldError, @Nullable Origin origin) {
      return fieldError != null && origin != null ? new OriginTrackedFieldError(fieldError, origin) : fieldError;
   }
}
