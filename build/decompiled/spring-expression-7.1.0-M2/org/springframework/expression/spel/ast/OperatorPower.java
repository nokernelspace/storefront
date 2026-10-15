package org.springframework.expression.spel.ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Operation;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.ExpressionState;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;

public class OperatorPower extends Operator {
   public OperatorPower(int startPos, int endPos, SpelNodeImpl... operands) {
      super("^", startPos, endPos, operands);
   }

   @Override
   public TypedValue getValueInternal(ExpressionState state) throws EvaluationException {
      Object leftOperand = this.getLeftOperand().getValueInternal(state).getValue();
      Object rightOperand = this.getRightOperand().getValueInternal(state).getValue();
      if (leftOperand instanceof Number leftNumber && rightOperand instanceof Number rightNumber) {
         state.trackOperation();
         if (leftNumber instanceof BigDecimal leftBigDecimal) {
            int exponent = rightNumber.intValue();
            this.checkBigNumberPowerBits(state, leftBigDecimal.unscaledValue().bitLength(), exponent);
            return new TypedValue(leftBigDecimal.pow(exponent));
         } else if (leftNumber instanceof BigInteger leftBigInteger) {
            int exponent = rightNumber.intValue();
            this.checkBigNumberPowerBits(state, leftBigInteger.bitLength(), exponent);
            return new TypedValue(leftBigInteger.pow(exponent));
         } else if (leftNumber instanceof Double || rightNumber instanceof Double) {
            return new TypedValue(Math.pow(leftNumber.doubleValue(), rightNumber.doubleValue()));
         } else if (!(leftNumber instanceof Float) && !(rightNumber instanceof Float)) {
            double d = Math.pow(leftNumber.doubleValue(), rightNumber.doubleValue());
            return !(d > 2.147483647E9) && !(leftNumber instanceof Long) && !(rightNumber instanceof Long) ? new TypedValue((int)d) : new TypedValue((long)d);
         } else {
            return new TypedValue(Math.pow(leftNumber.floatValue(), rightNumber.floatValue()));
         }
      } else {
         return state.operate(Operation.POWER, leftOperand, rightOperand);
      }
   }

   private void checkBigNumberPowerBits(ExpressionState state, int baseBitLength, int exponent) {
      int maxBigPowerBits = state.getConfiguration().getMaximumBigPowerBits();
      long estimatedBigPowerBits = (long)baseBitLength * exponent;
      if (estimatedBigPowerBits > maxBigPowerBits) {
         throw new SpelEvaluationException(this.getStartPosition(), SpelMessage.MAX_BIG_POWER_RESULT_EXCEEDED, baseBitLength, exponent, maxBigPowerBits);
      }
   }
}
