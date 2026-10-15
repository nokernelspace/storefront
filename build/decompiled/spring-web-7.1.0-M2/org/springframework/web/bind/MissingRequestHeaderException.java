package org.springframework.web.bind;

import org.springframework.core.MethodParameter;

public class MissingRequestHeaderException extends MissingRequestValueException {
   private final String headerName;
   private final MethodParameter parameter;

   public MissingRequestHeaderException(String headerName, MethodParameter parameter) {
      this(headerName, parameter, false);
   }

   public MissingRequestHeaderException(String headerName, MethodParameter parameter, boolean missingAfterConversion) {
      super("", missingAfterConversion, null, new Object[]{headerName});
      this.headerName = headerName;
      this.parameter = parameter;
      this.getBody().setDetail("Required header '" + this.headerName + "' is not present.");
   }

   public String getMessage() {
      String typeName = this.parameter.getNestedParameterType().getSimpleName();
      return "Required request header '"
         + this.headerName
         + "' for method parameter type "
         + typeName
         + " is "
         + (this.isMissingAfterConversion() ? "present but converted to null" : "not present");
   }

   public final String getHeaderName() {
      return this.headerName;
   }

   public final MethodParameter getParameter() {
      return this.parameter;
   }
}
