package org.springframework.expression.spel.ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.springframework.asm.MethodVisitor;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.Operation;
import org.springframework.expression.TypedValue;
import org.springframework.expression.spel.CodeFlow;
import org.springframework.expression.spel.ExpressionState;
import org.springframework.util.Assert;
import org.springframework.util.NumberUtils;

public class OpMinus extends Operator {
   public OpMinus(int startPos, int endPos, SpelNodeImpl... operands) {
      super("-", startPos, endPos, operands);
      if (this.isNegativeNumberLiteral()) {
         this.exitTypeDescriptor = ((Literal)operands[0]).exitTypeDescriptor;
      }
   }

   public boolean isNegativeNumberLiteral() {
      return this.children.length == 1 && this.children[0] instanceof Literal literal && literal.isNumberLiteral();
   }

   @Override
   public TypedValue getValueInternal(ExpressionState state) throws EvaluationException {
      SpelNodeImpl leftOp = this.getLeftOperand();
      if (this.children.length < 2) {
         Object operand = leftOp.getValueInternal(state).getValue();
         if (operand instanceof Number number) {
            state.trackOperation();
            if (number instanceof BigDecimal bigDecimal) {
               return new TypedValue(bigDecimal.negate());
            } else if (number instanceof BigInteger bigInteger) {
               return new TypedValue(bigInteger.negate());
            } else if (number instanceof Double) {
               this.exitTypeDescriptor = "D";
               return new TypedValue(0.0 - number.doubleValue());
            } else if (number instanceof Float) {
               this.exitTypeDescriptor = "F";
               return new TypedValue(0.0F - number.floatValue());
            } else if (number instanceof Long) {
               this.exitTypeDescriptor = "J";
               return new TypedValue(0L - number.longValue());
            } else if (number instanceof Integer) {
               this.exitTypeDescriptor = "I";
               return new TypedValue(0 - number.intValue());
            } else if (number instanceof Short) {
               return new TypedValue(0 - number.shortValue());
            } else {
               return number instanceof Byte ? new TypedValue(0 - number.byteValue()) : new TypedValue(0.0 - number.doubleValue());
            }
         } else {
            return state.operate(Operation.SUBTRACT, operand, null);
         }
      } else {
         Object left = leftOp.getValueInternal(state).getValue();
         Object right = this.getRightOperand().getValueInternal(state).getValue();
         if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            state.trackOperation();
            if (leftNumber instanceof BigDecimal || rightNumber instanceof BigDecimal) {
               BigDecimal leftBigDecimal = (BigDecimal)NumberUtils.convertNumberToTargetClass(leftNumber, BigDecimal.class);
               BigDecimal rightBigDecimal = (BigDecimal)NumberUtils.convertNumberToTargetClass(rightNumber, BigDecimal.class);
               return new TypedValue(leftBigDecimal.subtract(rightBigDecimal));
            }

            if (leftNumber instanceof Double || rightNumber instanceof Double) {
               this.exitTypeDescriptor = "D";
               return new TypedValue(leftNumber.doubleValue() - rightNumber.doubleValue());
            }

            if (leftNumber instanceof Float || rightNumber instanceof Float) {
               this.exitTypeDescriptor = "F";
               return new TypedValue(leftNumber.floatValue() - rightNumber.floatValue());
            }

            if (leftNumber instanceof BigInteger || rightNumber instanceof BigInteger) {
               BigInteger leftBigInteger = (BigInteger)NumberUtils.convertNumberToTargetClass(leftNumber, BigInteger.class);
               BigInteger rightBigInteger = (BigInteger)NumberUtils.convertNumberToTargetClass(rightNumber, BigInteger.class);
               return new TypedValue(leftBigInteger.subtract(rightBigInteger));
            }

            if (leftNumber instanceof Long || rightNumber instanceof Long) {
               this.exitTypeDescriptor = "J";
               return new TypedValue(leftNumber.longValue() - rightNumber.longValue());
            }

            if (!CodeFlow.isIntegerForNumericOp(leftNumber) && !CodeFlow.isIntegerForNumericOp(rightNumber)) {
               return new TypedValue(leftNumber.doubleValue() - rightNumber.doubleValue());
            }

            this.exitTypeDescriptor = "I";
            return new TypedValue(leftNumber.intValue() - rightNumber.intValue());
         } else if (left instanceof String theString && right instanceof Integer theInteger && theString.length() == 1) {
            state.trackOperation();
            return new TypedValue(Character.toString((char)(theString.charAt(0) - theInteger)));
         } else {
            return state.operate(Operation.SUBTRACT, left, right);
         }
      }
   }

   @Override
   public String toStringAST() {
      return this.children.length < 2 ? "-" + this.getLeftOperand().toStringAST() : super.toStringAST();
   }

   @Override
   public SpelNodeImpl getRightOperand() {
      if (this.children.length < 2) {
         throw new IllegalStateException("No right operand");
      } else {
         return this.children[1];
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
               mv.visitInsn(103);
               break;
            case 'E':
            case 'G':
            case 'H':
            default:
               throw new IllegalStateException("Unrecognized exit type descriptor: '" + this.exitTypeDescriptor + "'");
            case 'F':
               mv.visitInsn(102);
               break;
            case 'I':
               mv.visitInsn(100);
               break;
            case 'J':
               mv.visitInsn(101);
         }
      } else {
         switch (targetDesc) {
            case 'D':
               mv.visitInsn(119);
               break;
            case 'E':
            case 'G':
            case 'H':
            default:
               throw new IllegalStateException("Unrecognized exit type descriptor: '" + this.exitTypeDescriptor + "'");
            case 'F':
               mv.visitInsn(118);
               break;
            case 'I':
               mv.visitInsn(116);
               break;
            case 'J':
               mv.visitInsn(117);
         }
      }

      cf.pushDescriptor(this.exitTypeDescriptor);
   }
}
