package org.springframework.expression.spel.ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.jspecify.annotations.Nullable;
import org.springframework.asm.MethodVisitor;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Operation;
import org.springframework.expression.TypeConverter;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.CodeFlow;
import org.springframework.expression.spel.ExpressionState;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.util.Assert;
import org.springframework.util.NumberUtils;

public class OpPlus extends Operator {
   private static final int MAX_CONCATENATED_STRING_LENGTH = 100000;

   public OpPlus(int startPos, int endPos, SpelNodeImpl... operands) {
      super("+", startPos, endPos, operands);
      Assert.notEmpty(operands, "Operands must not be empty");
   }

   @Override
   public TypedValue getValueInternal(ExpressionState state) throws EvaluationException {
      SpelNodeImpl leftOp = this.getLeftOperand();
      if (this.children.length < 2) {
         Object operandOne = leftOp.getValueInternal(state).getValue();
         if (operandOne instanceof Number) {
            state.trackOperation();
            if (operandOne instanceof Double) {
               this.exitTypeDescriptor = "D";
            } else if (operandOne instanceof Float) {
               this.exitTypeDescriptor = "F";
            } else if (operandOne instanceof Long) {
               this.exitTypeDescriptor = "J";
            } else if (operandOne instanceof Integer) {
               this.exitTypeDescriptor = "I";
            }

            return new TypedValue(operandOne);
         } else {
            return state.operate(Operation.ADD, operandOne, null);
         }
      } else {
         TypedValue operandOneValue = leftOp.getValueInternal(state);
         Object leftOperand = operandOneValue.getValue();
         TypedValue operandTwoValue = this.getRightOperand().getValueInternal(state);
         Object rightOperand = operandTwoValue.getValue();
         if (leftOperand instanceof Number leftNumber && rightOperand instanceof Number rightNumber) {
            state.trackOperation();
            if (leftNumber instanceof BigDecimal || rightNumber instanceof BigDecimal) {
               BigDecimal leftBigDecimal = (BigDecimal)NumberUtils.convertNumberToTargetClass(leftNumber, BigDecimal.class);
               BigDecimal rightBigDecimal = (BigDecimal)NumberUtils.convertNumberToTargetClass(rightNumber, BigDecimal.class);
               return new TypedValue(leftBigDecimal.add(rightBigDecimal));
            }

            if (leftNumber instanceof Double || rightNumber instanceof Double) {
               this.exitTypeDescriptor = "D";
               return new TypedValue(leftNumber.doubleValue() + rightNumber.doubleValue());
            }

            if (leftNumber instanceof Float || rightNumber instanceof Float) {
               this.exitTypeDescriptor = "F";
               return new TypedValue(leftNumber.floatValue() + rightNumber.floatValue());
            }

            if (leftNumber instanceof BigInteger || rightNumber instanceof BigInteger) {
               BigInteger leftBigInteger = (BigInteger)NumberUtils.convertNumberToTargetClass(leftNumber, BigInteger.class);
               BigInteger rightBigInteger = (BigInteger)NumberUtils.convertNumberToTargetClass(rightNumber, BigInteger.class);
               return new TypedValue(leftBigInteger.add(rightBigInteger));
            }

            if (leftNumber instanceof Long || rightNumber instanceof Long) {
               this.exitTypeDescriptor = "J";
               return new TypedValue(leftNumber.longValue() + rightNumber.longValue());
            }

            if (!CodeFlow.isIntegerForNumericOp(leftNumber) && !CodeFlow.isIntegerForNumericOp(rightNumber)) {
               return new TypedValue(leftNumber.doubleValue() + rightNumber.doubleValue());
            }

            this.exitTypeDescriptor = "I";
            return new TypedValue(leftNumber.intValue() + rightNumber.intValue());
         } else if (leftOperand instanceof String leftString && rightOperand instanceof String rightString) {
            this.exitTypeDescriptor = "Ljava/lang/String";
            this.checkStringLength(leftString);
            this.checkStringLength(rightString);
            return this.concatenate(state, leftString, rightString);
         } else if (leftOperand instanceof String leftString) {
            this.checkStringLength(leftString);
            String rightString = rightOperand == null ? "null" : convertTypedValueToString(operandTwoValue, state);
            this.checkStringLength(rightString);
            return this.concatenate(state, leftString, rightString);
         } else if (rightOperand instanceof String rightString) {
            this.checkStringLength(rightString);
            String leftString = leftOperand == null ? "null" : convertTypedValueToString(operandOneValue, state);
            this.checkStringLength(leftString);
            return this.concatenate(state, leftString, rightString);
         } else {
            return state.operate(Operation.ADD, leftOperand, rightOperand);
         }
      }
   }

   private void checkStringLength(String string) {
      this.checkStringLength(string.length());
   }

   private void checkStringLength(int stringLength) {
      if (stringLength > 100000) {
         throw new SpelEvaluationException(this.getStartPosition(), SpelMessage.MAX_CONCATENATED_STRING_LENGTH_EXCEEDED, 100000);
      }
   }

   private TypedValue concatenate(ExpressionState state, String leftString, String rightString) {
      this.checkStringLength(leftString.length() + rightString.length());
      state.trackOperation();
      return new TypedValue(leftString + rightString);
   }

   @Override
   public String toStringAST() {
      return this.children.length < 2 ? "+" + this.getLeftOperand().toStringAST() : super.toStringAST();
   }

   @Override
   public SpelNodeImpl getRightOperand() {
      if (this.children.length < 2) {
         throw new IllegalStateException("No right operand");
      } else {
         return this.children[1];
      }
   }

   private static String convertTypedValueToString(TypedValue value, ExpressionState state) {
      TypeConverter typeConverter = state.getEvaluationContext().getTypeConverter();
      TypeDescriptor typeDescriptor = TypeDescriptor.valueOf(String.class);
      return typeConverter.canConvert(value.getTypeDescriptor(), typeDescriptor)
         ? String.valueOf(typeConverter.convertValue(value.getValue(), value.getTypeDescriptor(), typeDescriptor))
         : String.valueOf(value.getValue());
   }

   @Override
   public boolean isCompilable() {
      if (!this.getLeftOperand().isCompilable()) {
         return false;
      } else {
         return this.children.length > 1 && !this.getRightOperand().isCompilable() ? false : this.exitTypeDescriptor != null;
      }
   }

   private void walk(MethodVisitor mv, CodeFlow cf, @Nullable SpelNodeImpl operand) {
      if (operand instanceof OpPlus plus) {
         this.walk(mv, cf, plus.getLeftOperand());
         this.walk(mv, cf, plus.getRightOperand());
      } else if (operand != null) {
         cf.enterCompilationScope();
         operand.generateCode(mv, cf);
         if (!"Ljava/lang/String".equals(cf.lastDescriptor())) {
            mv.visitTypeInsn(192, "java/lang/String");
         }

         cf.exitCompilationScope();
         mv.visitMethodInsn(182, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;", false);
      }
   }

   @Override
   public void generateCode(MethodVisitor mv, CodeFlow cf) {
      if ("Ljava/lang/String".equals(this.exitTypeDescriptor)) {
         mv.visitTypeInsn(187, "java/lang/StringBuilder");
         mv.visitInsn(89);
         mv.visitMethodInsn(183, "java/lang/StringBuilder", "<init>", "()V", false);
         this.walk(mv, cf, this.getLeftOperand());
         this.walk(mv, cf, this.getRightOperand());
         mv.visitMethodInsn(182, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;", false);
      } else {
         this.children[0].generateCode(mv, cf);
         String leftDesc = this.children[0].exitTypeDescriptor;
         String exitDesc = this.exitTypeDescriptor;
         Assert.state(exitDesc != null, "No exit type descriptor");
         char targetDesc = exitDesc.charAt(0);
         CodeFlow.insertNumericUnboxOrPrimitiveTypeCoercion(mv, leftDesc, targetDesc);
         if (this.children.length > 1) {
            cf.enterCompilationScope();
            this.children[1].generateCode(mv, cf);
            String rightDesc = this.children[1].exitTypeDescriptor;
            cf.exitCompilationScope();
            CodeFlow.insertNumericUnboxOrPrimitiveTypeCoercion(mv, rightDesc, targetDesc);
            switch (targetDesc) {
               case 'D':
                  mv.visitInsn(99);
                  break;
               case 'E':
               case 'G':
               case 'H':
               default:
                  throw new IllegalStateException("Unrecognized exit type descriptor: '" + this.exitTypeDescriptor + "'");
               case 'F':
                  mv.visitInsn(98);
                  break;
               case 'I':
                  mv.visitInsn(96);
                  break;
               case 'J':
                  mv.visitInsn(97);
            }
         }
      }

      cf.pushDescriptor(this.exitTypeDescriptor);
   }
}
