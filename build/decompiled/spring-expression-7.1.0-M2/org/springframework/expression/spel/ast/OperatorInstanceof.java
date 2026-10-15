package org.springframework.expression.spel.ast;

import org.jspecify.annotations.Nullable;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Type;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.CodeFlow;
import org.springframework.expression.spel.ExpressionState;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.expression.spel.support.BooleanTypedValue;
import org.springframework.util.Assert;

public class OperatorInstanceof extends Operator {
   private @Nullable Class<?> type;

   public OperatorInstanceof(int startPos, int endPos, SpelNodeImpl... operands) {
      super("instanceof", startPos, endPos, operands);
   }

   public BooleanTypedValue getValueInternal(ExpressionState state) throws EvaluationException {
      SpelNodeImpl rightOperand = this.getRightOperand();
      TypedValue left = this.getLeftOperand().getValueInternal(state);
      TypedValue right = rightOperand.getValueInternal(state);
      Object leftValue = left.getValue();
      Object rightValue = right.getValue();
      state.trackOperation();
      if (rightValue instanceof Class<?> rightClass) {
         BooleanTypedValue result;
         if (leftValue == null) {
            result = BooleanTypedValue.FALSE;
         } else {
            result = BooleanTypedValue.forValue(rightClass.isInstance(leftValue));
         }

         this.type = rightClass;
         if (rightOperand instanceof TypeReference) {
            this.exitTypeDescriptor = "Z";
         }

         return result;
      } else {
         throw new SpelEvaluationException(
            this.getRightOperand().getStartPosition(),
            SpelMessage.INSTANCEOF_OPERATOR_NEEDS_CLASS_OPERAND,
            rightValue == null ? "null" : rightValue.getClass().getName()
         );
      }
   }

   @Override
   public boolean isCompilable() {
      return this.exitTypeDescriptor != null && this.getLeftOperand().isCompilable();
   }

   @Override
   public void generateCode(MethodVisitor mv, CodeFlow cf) {
      this.getLeftOperand().generateCode(mv, cf);
      CodeFlow.insertBoxIfNecessary(mv, cf.lastDescriptor());
      Assert.state(this.type != null, "No type available");
      if (this.type.isPrimitive()) {
         mv.visitInsn(87);
         mv.visitInsn(3);
      } else {
         mv.visitTypeInsn(193, Type.getInternalName(this.type));
      }

      cf.pushDescriptor(this.exitTypeDescriptor);
   }
}
