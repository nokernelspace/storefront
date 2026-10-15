package org.springframework.web.bind;

import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;

public class MissingServletRequestParameterException extends MissingRequestValueException {
   private final String parameterName;
   private final String parameterType;
   private final @Nullable MethodParameter parameter;

   public MissingServletRequestParameterException(String parameterName, String parameterType) {
      super("", false, null, new Object[]{parameterName});
      this.parameterName = parameterName;
      this.parameterType = parameterType;
      this.parameter = null;
      this.getBody().setDetail(initBodyDetail(this.parameterName));
   }

   public MissingServletRequestParameterException(String parameterName, MethodParameter parameter, boolean missingAfterConversion) {
      super("", missingAfterConversion, null, new Object[]{parameterName});
      this.parameterName = parameterName;
      this.parameterType = parameter.getNestedParameterType().getSimpleName();
      this.parameter = parameter;
      this.getBody().setDetail(initBodyDetail(this.parameterName));
   }

   private static String initBodyDetail(String name) {
      return "Required parameter '" + name + "' is not present.";
   }

   public String getMessage() {
      return "Required request parameter '"
         + this.parameterName
         + "' for method parameter type "
         + this.parameterType
         + " is "
         + (this.isMissingAfterConversion() ? "present but converted to null" : "not present");
   }

   public final String getParameterName() {
      return this.parameterName;
   }

   public final String getParameterType() {
      return this.parameterType;
   }

   public @Nullable MethodParameter getMethodParameter() {
      return this.parameter;
   }
}
