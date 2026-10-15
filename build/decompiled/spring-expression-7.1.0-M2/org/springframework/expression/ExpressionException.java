package org.springframework.expression;

import org.jspecify.annotations.Nullable;

public class ExpressionException extends RuntimeException {
   protected final @Nullable String expressionString;
   protected int position;

   public ExpressionException(String message) {
      super(message);
      this.expressionString = null;
      this.position = 0;
   }

   public ExpressionException(String message, @Nullable Throwable cause) {
      super(message, cause);
      this.expressionString = null;
      this.position = 0;
   }

   public ExpressionException(@Nullable String expressionString, String message) {
      super(message);
      this.expressionString = expressionString;
      this.position = -1;
   }

   public ExpressionException(@Nullable String expressionString, int position, String message) {
      super(message);
      this.expressionString = expressionString;
      this.position = position;
   }

   public ExpressionException(int position, String message) {
      super(message);
      this.expressionString = null;
      this.position = position;
   }

   public ExpressionException(int position, String message, @Nullable Throwable cause) {
      super(message, cause);
      this.expressionString = null;
      this.position = position;
   }

   public final @Nullable String getExpressionString() {
      return this.expressionString;
   }

   public final int getPosition() {
      return this.position;
   }

   @Override
   public String getMessage() {
      return this.toDetailedString();
   }

   public String toDetailedString() {
      if (this.expressionString != null) {
         StringBuilder output = new StringBuilder();
         output.append("Expression [");
         output.append(this.expressionString);
         output.append(']');
         if (this.position >= 0) {
            output.append(" @");
            output.append(this.position);
         }

         output.append(": ");
         output.append(this.getSimpleMessage());
         return output.toString();
      } else {
         return this.getSimpleMessage();
      }
   }

   public String getSimpleMessage() {
      return super.getMessage();
   }
}
