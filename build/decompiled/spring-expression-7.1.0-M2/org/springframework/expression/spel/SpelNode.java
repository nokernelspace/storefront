package org.springframework.expression.spel;

import org.jspecify.annotations.Nullable;
import org.springframework.asm.MethodVisitor;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.TypedValue;

public interface SpelNode {
   @Nullable Object getValue(ExpressionState expressionState) throws EvaluationException;

   TypedValue getTypedValue(ExpressionState expressionState) throws EvaluationException;

   boolean isWritable(ExpressionState expressionState) throws EvaluationException;

   void setValue(ExpressionState expressionState, @Nullable Object newValue) throws EvaluationException;

   String toStringAST();

   int getChildCount();

   SpelNode getChild(int index);

   @Nullable Class<?> getObjectClass(@Nullable Object obj);

   int getStartPosition();

   int getEndPosition();

   default boolean isCompilable() {
      return false;
   }

   default void generateCode(MethodVisitor methodVisitor, CodeFlow codeFlow) {
      throw new IllegalStateException(this.getClass().getName() + " does not support bytecode generation");
   }
}
