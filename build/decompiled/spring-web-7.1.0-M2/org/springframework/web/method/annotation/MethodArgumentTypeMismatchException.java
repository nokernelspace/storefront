package org.springframework.web.method.annotation;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.MethodParameter;

public class MethodArgumentTypeMismatchException extends TypeMismatchException {
   private final String name;
   private final MethodParameter parameter;

   public MethodArgumentTypeMismatchException(
      @Nullable Object value, @Nullable Class<?> requiredType, String name, MethodParameter param, @Nullable Throwable cause
   ) {
      super(value, requiredType, cause);
      this.name = name;
      this.parameter = param;
      this.initPropertyName(name);
   }

   public String getName() {
      return this.name;
   }

   public MethodParameter getParameter() {
      return this.parameter;
   }

   public String getMessage() {
      return "Method parameter '" + this.getName() + "': " + super.getMessage();
   }
}
