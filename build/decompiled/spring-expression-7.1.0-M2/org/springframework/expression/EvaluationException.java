package org.springframework.expression;

import org.jspecify.annotations.Nullable;

public class EvaluationException extends ExpressionException {
   public EvaluationException(String message) {
      super(message);
   }

   public EvaluationException(String message, @Nullable Throwable cause) {
      super(message, cause);
   }

   public EvaluationException(int position, String message) {
      super(position, message);
   }

   public EvaluationException(String expressionString, String message) {
      super(expressionString, message);
   }

   public EvaluationException(int position, String message, @Nullable Throwable cause) {
      super(position, message, cause);
   }
}
