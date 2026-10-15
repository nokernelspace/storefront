package org.springframework.beans;

import java.beans.PropertyChangeEvent;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

public class InvalidPropertyPathException extends PropertyAccessException {
   public static final String ERROR_CODE = "invalidPropertyPath";
   private final String propertyPath;

   public InvalidPropertyPathException(String propertyPath, String reason) {
      super("Invalid property path '" + propertyPath + "': " + reason, null);
      this.propertyPath = propertyPath;
   }

   public InvalidPropertyPathException(PropertyChangeEvent propertyChangeEvent, InvalidPropertyPathException cause) {
      super(propertyChangeEvent, Objects.requireNonNull(cause.getMessage()), cause);
      this.propertyPath = cause.propertyPath;
   }

   public InvalidPropertyPathException(Object source, String propertyName, @Nullable Object newValue, InvalidPropertyPathException cause) {
      this(new PropertyChangeEvent(source, propertyName, null, newValue), cause);
   }

   public String getPropertyPath() {
      return this.propertyPath;
   }

   @Override
   public String getErrorCode() {
      return "invalidPropertyPath";
   }
}
