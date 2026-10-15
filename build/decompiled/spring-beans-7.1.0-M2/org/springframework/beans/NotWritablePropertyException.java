package org.springframework.beans;

import org.jspecify.annotations.Nullable;

public class NotWritablePropertyException extends InvalidPropertyException {
   private final String @Nullable [] possibleMatches;

   public NotWritablePropertyException(Class<?> beanClass, String propertyName) {
      super(
         beanClass,
         propertyName,
         "Bean property '"
            + propertyName
            + "' is not writable or has an invalid setter method: Does the return type of the getter match the parameter type of the setter?"
      );
      this.possibleMatches = null;
   }

   public NotWritablePropertyException(Class<?> beanClass, String propertyName, String msg) {
      super(beanClass, propertyName, msg);
      this.possibleMatches = null;
   }

   public NotWritablePropertyException(Class<?> beanClass, String propertyName, String msg, Throwable cause) {
      super(beanClass, propertyName, msg, cause);
      this.possibleMatches = null;
   }

   public NotWritablePropertyException(Class<?> beanClass, String propertyName, String msg, String[] possibleMatches) {
      super(beanClass, propertyName, msg);
      this.possibleMatches = possibleMatches;
   }

   public String @Nullable [] getPossibleMatches() {
      return this.possibleMatches;
   }
}
