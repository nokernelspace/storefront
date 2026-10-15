package org.springframework.beans;

import java.beans.PropertyChangeEvent;
import org.jspecify.annotations.Nullable;

public abstract class PropertyAccessException extends BeansException {
   private final @Nullable PropertyChangeEvent propertyChangeEvent;

   public PropertyAccessException(PropertyChangeEvent propertyChangeEvent, String msg, @Nullable Throwable cause) {
      super(msg, cause);
      this.propertyChangeEvent = propertyChangeEvent;
   }

   public PropertyAccessException(String msg, @Nullable Throwable cause) {
      super(msg, cause);
      this.propertyChangeEvent = null;
   }

   public @Nullable PropertyChangeEvent getPropertyChangeEvent() {
      return this.propertyChangeEvent;
   }

   public @Nullable String getPropertyName() {
      return this.propertyChangeEvent != null ? this.propertyChangeEvent.getPropertyName() : null;
   }

   public @Nullable Object getValue() {
      return this.propertyChangeEvent != null ? this.propertyChangeEvent.getNewValue() : null;
   }

   public abstract String getErrorCode();
}
