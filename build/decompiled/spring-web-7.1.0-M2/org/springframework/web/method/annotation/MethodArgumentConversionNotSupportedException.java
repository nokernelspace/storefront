package org.springframework.web.method.annotation;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.core.MethodParameter;

public class MethodArgumentConversionNotSupportedException extends ConversionNotSupportedException {
   private final String name;
   private final MethodParameter parameter;

   public MethodArgumentConversionNotSupportedException(
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
}
