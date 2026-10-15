package org.springframework.expression.spel.ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.springframework.asm.MethodVisitor;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Operation;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.CodeFlow;
import org.springframework.expression.spel.ExpressionState;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.util.Assert;
import org.springframework.util.NumberUtils;

public class OpMultiply extends Operator {
   private static final int MAX_REPEATED_TEXT_SIZE = 256;

   public OpMultiply(int startPos, int endPos, SpelNodeImpl... operands) {
      super("*", startPos, endPos, operands);
   }

   @Override
   public TypedValue getValueInternal(ExpressionState state) throws EvaluationException {
      Object leftOperand = this.getLeftOperand().getValueInternal(state).getValue();
      Object rightOperand = this.getRightOperand().getValueInternal(state).getValue();
      if (leftOperand instanceof Number leftNumber && rightOperand instanceof Number rightNumber) {
         state.trackOperation();
         if (leftNumber instanceof BigDecimal || rightNumber instanceof BigDecimal) {
            BigDecimal leftBigDecimal = (BigDecimal)NumberUtils.convertNumberToTargetClass(leftNumber, BigDecimal.class);
            BigDecimal rightBigDecimal = (BigDecimal)NumberUtils.convertNumberToTargetClass(rightNumber, BigDecimal.class);
            return new TypedValue(leftBigDecimal.multiply(rightBigDecimal));
         }

         if (leftNumber instanceof Double || rightNumber instanceof Double) {
            this.exitTypeDescriptor = "D";
            return new TypedValue(leftNumber.doubleValue() * rightNumber.doubleValue());
         }

         if (leftNumber instanceof Float || rightNumber instanceof Float) {
            this.exitTypeDescriptor = "F";
            return new TypedValue(leftNumber.floatValue() * rightNumber.floatValue());
         }

         if (leftNumber instanceof BigInteger || rightNumber instanceof BigInteger) {
            BigInteger leftBigInteger = (BigInteger)NumberUtils.convertNumberToTargetClass(leftNumber, BigInteger.class);
            BigInteger rightBigInteger = (BigInteger)NumberUtils.convertNumberToTargetClass(rightNumber, BigInteger.class);
            return new TypedValue(leftBigInteger.multiply(rightBigInteger));
         }

         if (leftNumber instanceof Long || rightNumber instanceof Long) {
            this.exitTypeDescriptor = "J";
            return new TypedValue(leftNumber.longValue() * rightNumber.longValue());
         }

         if (!CodeFlow.isIntegerForNumericOp(leftNumber) && !CodeFlow.isIntegerForNumericOp(rightNumber)) {
            return new TypedValue(leftNumber.doubleValue() * rightNumber.doubleValue());
         }

         this.exitTypeDescriptor = "I";
         return new TypedValue(leftNumber.intValue() * rightNumber.intValue());
      } else if (leftOperand instanceof String text && rightOperand instanceof Integer count) {
         this.checkRepeatedTextSize(text, count);
         state.trackOperation();
         return new TypedValue(text.repeat(count));
      } else {
         return state.operate(Operation.MULTIPLY, leftOperand, rightOperand);
      }
   }

   private void checkRepeatedTextSize(String text, int count) {
      if (count < 0) {
         throw new SpelEvaluationException(this.getStartPosition(), SpelMessage.NEGATIVE_REPEATED_TEXT_COUNT, count);
      } else {
         long result = (long)text.length() * count;
         if (result < 0L || result > 256L) {
            throw new SpelEvaluationException(this.getRightOperand().getStartPosition(), SpelMessage.MAX_REPEATED_TEXT_SIZE_EXCEEDED, 256);
         }
      }
   }

   @Override
   public boolean isCompilable() {
      if (!this.getLeftOperand().isCompilable()) {
         return false;
      } else {
         return this.children.length > 1 && !this.getRightOperand().isCompilable() ? false : this.exitTypeDescriptor != null;
      }
   }

   @Override
   public void generateCode(MethodVisitor mv, CodeFlow cf) {
      this.getLeftOperand().generateCode(mv, cf);
      String leftDesc = this.getLeftOperand().exitTypeDescriptor;
      String exitDesc = this.exitTypeDescriptor;
      Assert.state(exitDesc != null, "No exit type descriptor");
      char targetDesc = exitDesc.charAt(0);
      CodeFlow.insertNumericUnboxOrPrimitiveTypeCoercion(mv, leftDesc, targetDesc);
      if (this.children.length > 1) {
         cf.enterCompilationScope();
         this.getRightOperand().generateCode(mv, cf);
         String rightDesc = this.getRightOperand().exitTypeDescriptor;
         cf.exitCompilationScope();
         CodeFlow.insertNumericUnboxOrPrimitiveTypeCoercion(mv, rightDesc, targetDesc);
         switch (targetDesc) {
            case 'D':
               mv.visitInsn(107);
               break;
            case 'E':
            case 'G':
            case 'H':
            default:
               throw new IllegalStateException("Unrecognized exit type descriptor: '" + this.exitTypeDescriptor + "'");
            case 'F':
               mv.visitInsn(106);
               break;
            case 'I':
               mv.visitInsn(104);
               break;
            case 'J':
               mv.visitInsn(105);
         }
      }

      cf.pushDescriptor(this.exitTypeDescriptor);
   }
}
