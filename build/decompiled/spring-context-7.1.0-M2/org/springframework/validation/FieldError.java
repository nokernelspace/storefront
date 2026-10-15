package org.springframework.validation;

import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

public class FieldError extends ObjectError {
   private final String field;
   private final @Nullable Object rejectedValue;
   private final boolean bindingFailure;

   public FieldError(String objectName, String field, String defaultMessage) {
      this(objectName, field, null, false, null, null, defaultMessage);
   }

   public FieldError(
      String objectName,
      String field,
      @Nullable Object rejectedValue,
      boolean bindingFailure,
      String @Nullable [] codes,
      Object @Nullable [] arguments,
      @Nullable String defaultMessage
   ) {
      super(objectName, codes, arguments, defaultMessage);
      Assert.notNull(field, "Field must not be null");
      this.field = field;
      this.rejectedValue = rejectedValue;
      this.bindingFailure = bindingFailure;
   }

   public String getField() {
      return this.field;
   }

   public @Nullable Object getRejectedValue() {
      return this.rejectedValue;
   }

   public boolean isBindingFailure() {
      return this.bindingFailure;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      if (this == other) {
         return true;
      } else {
         return !super.equals(other)
            ? false
            : other instanceof FieldError otherError
               && this.getField().equals(otherError.getField())
               && ObjectUtils.nullSafeEquals(this.getRejectedValue(), otherError.getRejectedValue())
               && this.isBindingFailure() == otherError.isBindingFailure();
      }
   }

   @Override
   public int hashCode() {
      int hashCode = super.hashCode();
      hashCode = 29 * hashCode + this.getField().hashCode();
      hashCode = 29 * hashCode + ObjectUtils.nullSafeHashCode(this.getRejectedValue());
      return 29 * hashCode + (this.isBindingFailure() ? 1 : 0);
   }

   @Override
   public String toString() {
      return "Field error in object '"
         + this.getObjectName()
         + "' on field '"
         + this.field
         + "': rejected value ["
         + ObjectUtils.nullSafeToString(this.rejectedValue)
         + "]; "
         + this.resolvableToString();
   }
}
