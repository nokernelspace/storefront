package org.springframework.aop.support;

import java.io.Serializable;
import org.jspecify.annotations.Nullable;

public abstract class AbstractExpressionPointcut implements ExpressionPointcut, Serializable {
   private @Nullable String location;
   private @Nullable String expression;

   public void setLocation(@Nullable String location) {
      this.location = location;
   }

   public @Nullable String getLocation() {
      return this.location;
   }

   public void setExpression(@Nullable String expression) {
      this.expression = expression;

      try {
         this.onSetExpression(expression);
      } catch (IllegalArgumentException ex) {
         if (this.location != null) {
            throw new IllegalArgumentException("Invalid expression at location [" + this.location + "]: " + ex);
         } else {
            throw ex;
         }
      }
   }

   protected void onSetExpression(@Nullable String expression) throws IllegalArgumentException {
   }

   @Override
   public @Nullable String getExpression() {
      return this.expression;
   }
}
