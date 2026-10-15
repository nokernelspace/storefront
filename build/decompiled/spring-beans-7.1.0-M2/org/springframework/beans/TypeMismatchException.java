package org.springframework.beans;

import java.beans.PropertyChangeEvent;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

public class TypeMismatchException extends PropertyAccessException {
   public static final String ERROR_CODE = "typeMismatch";
   private @Nullable String propertyName;
   private final transient @Nullable Object value;
   private final @Nullable Class<?> requiredType;

   public TypeMismatchException(PropertyChangeEvent propertyChangeEvent, Class<?> requiredType) {
      this(propertyChangeEvent, requiredType, null);
   }

   public TypeMismatchException(PropertyChangeEvent propertyChangeEvent, @Nullable Class<?> requiredType, @Nullable Throwable cause) {
      super(
         propertyChangeEvent,
         "Failed to convert property value of type '"
            + ClassUtils.getDescriptiveType(propertyChangeEvent.getNewValue())
            + "'"
            + (requiredType != null ? " to required type '" + ClassUtils.getQualifiedName(requiredType) + "'" : "")
            + (propertyChangeEvent.getPropertyName() != null ? " for property '" + propertyChangeEvent.getPropertyName() + "'" : "")
            + (cause != null ? "; " + cause.getMessage() : ""),
         cause
      );
      this.propertyName = propertyChangeEvent.getPropertyName();
      this.value = propertyChangeEvent.getNewValue();
      this.requiredType = requiredType;
   }

   public TypeMismatchException(@Nullable Object value, @Nullable Class<?> requiredType) {
      this(value, requiredType, null);
   }

   public TypeMismatchException(@Nullable Object value, @Nullable Class<?> requiredType, @Nullable Throwable cause) {
      super(
         "Failed to convert value of type '"
            + ClassUtils.getDescriptiveType(value)
            + "'"
            + (requiredType != null ? " to required type '" + ClassUtils.getQualifiedName(requiredType) + "'" : "")
            + (cause != null ? "; " + cause.getMessage() : ""),
         cause
      );
      this.value = value;
      this.requiredType = requiredType;
   }

   public void initPropertyName(String propertyName) {
      Assert.state(this.propertyName == null, "Property name already initialized");
      this.propertyName = propertyName;
   }

   @Override
   public @Nullable String getPropertyName() {
      return this.propertyName;
   }

   @Override
   public @Nullable Object getValue() {
      return this.value;
   }

   public @Nullable Class<?> getRequiredType() {
      return this.requiredType;
   }

   @Override
   public String getErrorCode() {
      return "typeMismatch";
   }
}
