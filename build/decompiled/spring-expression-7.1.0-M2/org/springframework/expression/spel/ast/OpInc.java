package org.springframework.expression.spel.ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Operation;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.ExpressionState;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.util.Assert;

public class OpInc extends Operator {
   private static final String INC = "++";
   private final boolean postfix;

   public OpInc(int startPos, int endPos, boolean postfix, SpelNodeImpl... operands) {
      super("++", startPos, endPos, operands);
      this.postfix = postfix;
      Assert.notEmpty(operands, "Operands must not be empty");
   }

   @Override
   public TypedValue getValueInternal(ExpressionState state) throws EvaluationException {
      if (!state.getEvaluationContext().isAssignmentEnabled()) {
         throw new SpelEvaluationException(this.getStartPosition(), SpelMessage.OPERAND_NOT_INCREMENTABLE, this.toStringAST());
      }

      SpelNodeImpl operand = this.getLeftOperand();
      ValueRef valueRef = operand.getValueRef(state);
      TypedValue typedValue = valueRef.getValue();
      Object value = typedValue.getValue();
      TypedValue returnValue = typedValue;
      TypedValue newValue = null;
      if (value instanceof Number op1) {
         state.trackOperation();
         if (op1 instanceof BigDecimal bigDecimal) {
            newValue = new TypedValue(bigDecimal.add(BigDecimal.ONE), typedValue.getTypeDescriptor());
         } else if (op1 instanceof Double) {
            newValue = new TypedValue(op1.doubleValue() + 1.0, typedValue.getTypeDescriptor());
         } else if (op1 instanceof Float) {
            newValue = new TypedValue(op1.floatValue() + 1.0F, typedValue.getTypeDescriptor());
         } else if (op1 instanceof BigInteger bigInteger) {
            newValue = new TypedValue(bigInteger.add(BigInteger.ONE), typedValue.getTypeDescriptor());
         } else if (op1 instanceof Long) {
            newValue = new TypedValue(op1.longValue() + 1L, typedValue.getTypeDescriptor());
         } else if (op1 instanceof Integer) {
            newValue = new TypedValue(op1.intValue() + 1, typedValue.getTypeDescriptor());
         } else if (op1 instanceof Short) {
            newValue = new TypedValue(op1.shortValue() + 1, typedValue.getTypeDescriptor());
         } else if (op1 instanceof Byte) {
            newValue = new TypedValue(op1.byteValue() + 1, typedValue.getTypeDescriptor());
         } else {
            newValue = new TypedValue(op1.doubleValue() + 1.0, typedValue.getTypeDescriptor());
         }
      }

      if (newValue == null) {
         try {
            newValue = state.operate(Operation.ADD, returnValue.getValue(), 1);
         } catch (SpelEvaluationException ex) {
            if (ex.getMessageCode() == SpelMessage.OPERATOR_NOT_SUPPORTED_BETWEEN_TYPES) {
               throw new SpelEvaluationException(operand.getStartPosition(), SpelMessage.OPERAND_NOT_INCREMENTABLE, operand.toStringAST());
            }

            throw ex;
         }
      }

      state.trackOperation();

      try {
         valueRef.setValue(newValue.getValue());
      } catch (SpelEvaluationException see) {
         if (see.getMessageCode() == SpelMessage.SETVALUE_NOT_SUPPORTED) {
            throw new SpelEvaluationException(operand.getStartPosition(), SpelMessage.OPERAND_NOT_INCREMENTABLE);
         }

         throw see;
      }

      if (!this.postfix) {
         returnValue = newValue;
      }

      return returnValue;
   }

   @Override
   public String toStringAST() {
      String ast = this.getLeftOperand().toStringAST();
      return this.postfix ? ast + "++" : "++" + ast;
   }

   @Override
   public SpelNodeImpl getRightOperand() {
      throw new IllegalStateException("No right operand");
   }
}
