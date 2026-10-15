package org.springframework.cache.interceptor;

import org.springframework.expression.EvaluationException;

class VariableNotAvailableException extends EvaluationException {
   public VariableNotAvailableException(String name) {
      super("Variable '" + name + "' not available");
   }
}
