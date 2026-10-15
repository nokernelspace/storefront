package org.springframework.expression.spel.ast;

import org.jspecify.annotations.Nullable;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.ExpressionState;

public class QualifiedIdentifier extends SpelNodeImpl {
   private @Nullable TypedValue value;

   public QualifiedIdentifier(int startPos, int endPos, SpelNodeImpl... operands) {
      super(startPos, endPos, operands);
   }

   @Override
   public TypedValue getValueInternal(ExpressionState state) throws EvaluationException {
      if (this.value == null) {
         StringBuilder sb = new StringBuilder();

         for (int i = 0; i < this.getChildCount(); i++) {
            Object value = this.children[i].getValueInternal(state).getValue();
            if (i > 0 && (value == null || !value.toString().startsWith("$"))) {
               sb.append('.');
            }

            sb.append(value);
         }

         this.value = new TypedValue(sb.toString());
      }

      return this.value;
   }

   @Override
   public String toStringAST() {
      StringBuilder sb = new StringBuilder();
      if (this.value != null) {
         sb.append(this.value.getValue());
      } else {
         for (int i = 0; i < this.getChildCount(); i++) {
            if (i > 0) {
               sb.append('.');
            }

            sb.append(this.getChild(i).toStringAST());
         }
      }

      return sb.toString();
   }
}
