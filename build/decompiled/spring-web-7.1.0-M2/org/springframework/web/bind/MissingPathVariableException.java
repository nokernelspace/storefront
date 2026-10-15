package org.springframework.web.bind;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public class MissingPathVariableException extends MissingRequestValueException {
   private final String variableName;
   private final MethodParameter parameter;

   public MissingPathVariableException(String variableName, MethodParameter parameter) {
      this(variableName, parameter, false);
   }

   public MissingPathVariableException(String variableName, MethodParameter parameter, boolean missingAfterConversion) {
      super("", missingAfterConversion, null, new Object[]{variableName});
      this.variableName = variableName;
      this.parameter = parameter;
      this.getBody().setDetail("Required path variable '" + this.variableName + "' is not present.");
   }

   public String getMessage() {
      return "Required URI template variable '"
         + this.variableName
         + "' for method parameter type "
         + this.parameter.getNestedParameterType().getSimpleName()
         + " is "
         + (this.isMissingAfterConversion() ? "present but converted to null" : "not present");
   }

   public final String getVariableName() {
      return this.variableName;
   }

   public final MethodParameter getParameter() {
      return this.parameter;
   }

   @Override
   public HttpStatusCode getStatusCode() {
      return this.isMissingAfterConversion() ? HttpStatus.BAD_REQUEST : HttpStatus.INTERNAL_SERVER_ERROR;
   }
}
