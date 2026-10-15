package org.springframework.web.bind;

import org.springframework.core.MethodParameter;

public class MissingMatrixVariableException extends MissingRequestValueException {
   private final String variableName;
   private final MethodParameter parameter;

   public MissingMatrixVariableException(String variableName, MethodParameter parameter) {
      this(variableName, parameter, false);
   }

   public MissingMatrixVariableException(String variableName, MethodParameter parameter, boolean missingAfterConversion) {
      super("", missingAfterConversion, null, new Object[]{variableName});
      this.variableName = variableName;
      this.parameter = parameter;
      this.getBody().setDetail("Required path parameter '" + this.variableName + "' is not present.");
   }

   public String getMessage() {
      return "Required matrix variable '"
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
}
