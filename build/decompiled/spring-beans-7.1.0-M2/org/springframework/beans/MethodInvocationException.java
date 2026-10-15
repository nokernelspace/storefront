package org.springframework.beans;

import java.beans.PropertyChangeEvent;
import org.jspecify.annotations.Nullable;

public class MethodInvocationException extends PropertyAccessException {
   public static final String ERROR_CODE = "methodInvocation";

   public MethodInvocationException(PropertyChangeEvent propertyChangeEvent, @Nullable Throwable cause) {
      super(propertyChangeEvent, "Property '" + propertyChangeEvent.getPropertyName() + "' threw exception: " + cause, cause);
   }

   @Override
   public String getErrorCode() {
      return "methodInvocation";
   }
}
