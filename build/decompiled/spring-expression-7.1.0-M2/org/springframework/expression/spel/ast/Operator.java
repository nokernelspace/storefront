package org.springframework.expression.spel.ast;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.asm.Label;
import org.springframework.asm.MethodVisitor;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.spel.CodeFlow;
import org.springframework.util.NumberUtils;
import org.springframework.util.ObjectUtils;

public abstract class Operator extends SpelNodeImpl {
   private final String operatorName;
   protected @Nullable String leftActualDescriptor;
   protected @Nullable String rightActualDescriptor;

   public Operator(String payload, int startPos, int endPos, SpelNodeImpl... operands) {
      super(startPos, endPos, operands);
      this.operatorName = payload;
   }

   public SpelNodeImpl getLeftOperand() {
      return this.children[0];
   }

   public SpelNodeImpl getRightOperand() {
      return this.children[1];
   }

   public final String getOperatorName() {
      return this.operatorName;
   }

   @Override
   public String toStringAST() {
      StringBuilder sb = new StringBuilder("(");
      sb.append(this.getChild(0).toStringAST());

      for (int i = 1; i < this.getChildCount(); i++) {
         sb.append(' ').append(this.getOperatorName()).append(' ');
         sb.append(this.getChild(i).toStringAST());
      }

      sb.append(')');
      return sb.toString();
   }

   protected boolean isCompilableOperatorUsingNumerics() {
      SpelNodeImpl left = this.getLeftOperand();
      SpelNodeImpl right = this.getRightOperand();
      if (left.isCompilable() && right.isCompilable()) {
         String leftDesc = left.exitTypeDescriptor;
         String rightDesc = right.exitTypeDescriptor;
         Operator.DescriptorComparison dc = Operator.DescriptorComparison.checkNumericCompatibility(
            leftDesc, rightDesc, this.leftActualDescriptor, this.rightActualDescriptor
         );
         return dc.areNumbers && dc.areCompatible;
      } else {
         return false;
      }
   }

   protected void generateComparisonCode(MethodVisitor mv, CodeFlow cf, int compInstruction1, int compInstruction2) {
      SpelNodeImpl left = this.getLeftOperand();
      SpelNodeImpl right = this.getRightOperand();
      String leftDesc = left.exitTypeDescriptor;
      String rightDesc = right.exitTypeDescriptor;
      Label elseTarget = new Label();
      Label endOfIf = new Label();
      boolean unboxLeft = !CodeFlow.isPrimitive(leftDesc);
      boolean unboxRight = !CodeFlow.isPrimitive(rightDesc);
      Operator.DescriptorComparison dc = Operator.DescriptorComparison.checkNumericCompatibility(
         leftDesc, rightDesc, this.leftActualDescriptor, this.rightActualDescriptor
      );
      char targetType = dc.compatibleType;
      cf.enterCompilationScope();
      left.generateCode(mv, cf);
      cf.exitCompilationScope();
      if (CodeFlow.isPrimitive(leftDesc)) {
         CodeFlow.insertBoxIfNecessary(mv, leftDesc);
         unboxLeft = true;
      }

      cf.enterCompilationScope();
      right.generateCode(mv, cf);
      cf.exitCompilationScope();
      if (CodeFlow.isPrimitive(rightDesc)) {
         CodeFlow.insertBoxIfNecessary(mv, rightDesc);
         unboxRight = true;
      }

      Label rightIsNonNull = new Label();
      mv.visitInsn(89);
      mv.visitJumpInsn(199, rightIsNonNull);
      mv.visitInsn(95);
      Label leftNotNullRightIsNull = new Label();
      mv.visitJumpInsn(199, leftNotNullRightIsNull);
      mv.visitInsn(87);
      switch (compInstruction1) {
         case 155:
         case 157:
            mv.visitInsn(4);
            break;
         case 156:
         case 158:
            mv.visitInsn(3);
            break;
         default:
            throw new IllegalStateException("Unsupported: " + compInstruction1);
      }

      mv.visitJumpInsn(167, endOfIf);
      mv.visitLabel(leftNotNullRightIsNull);
      mv.visitInsn(87);
      switch (compInstruction1) {
         case 155:
         case 158:
            mv.visitInsn(4);
            break;
         case 156:
         case 157:
            mv.visitInsn(3);
            break;
         default:
            throw new IllegalStateException("Unsupported: " + compInstruction1);
      }

      mv.visitJumpInsn(167, endOfIf);
      mv.visitLabel(rightIsNonNull);
      mv.visitInsn(95);
      mv.visitInsn(89);
      Label neitherRightNorLeftAreNull = new Label();
      mv.visitJumpInsn(199, neitherRightNorLeftAreNull);
      mv.visitInsn(88);
      switch (compInstruction1) {
         case 155:
         case 158:
            mv.visitInsn(3);
            break;
         case 156:
         case 157:
            mv.visitInsn(4);
            break;
         default:
            throw new IllegalStateException("Unsupported: " + compInstruction1);
      }

      mv.visitJumpInsn(167, endOfIf);
      mv.visitLabel(neitherRightNorLeftAreNull);
      if (unboxLeft) {
         CodeFlow.insertUnboxInsns(mv, targetType, leftDesc);
      }

      if (targetType != 'D' && targetType != 'J') {
         mv.visitInsn(95);
      } else {
         mv.visitInsn(93);
         mv.visitInsn(88);
      }

      if (unboxRight) {
         CodeFlow.insertUnboxInsns(mv, targetType, rightDesc);
      }

      if (targetType == 'D') {
         mv.visitInsn(152);
         mv.visitJumpInsn(compInstruction1, elseTarget);
      } else if (targetType == 'F') {
         mv.visitInsn(150);
         mv.visitJumpInsn(compInstruction1, elseTarget);
      } else if (targetType == 'J') {
         mv.visitInsn(148);
         mv.visitJumpInsn(compInstruction1, elseTarget);
      } else {
         if (targetType != 'I') {
            throw new IllegalStateException("Unexpected descriptor " + leftDesc);
         }

         mv.visitJumpInsn(compInstruction2, elseTarget);
      }

      mv.visitInsn(4);
      mv.visitJumpInsn(167, endOfIf);
      mv.visitLabel(elseTarget);
      mv.visitInsn(3);
      mv.visitLabel(endOfIf);
      cf.pushDescriptor("Z");
   }

   public static boolean equalityCheck(EvaluationContext context, @Nullable Object left, @Nullable Object right) {
      if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
         if (leftNumber instanceof BigDecimal || rightNumber instanceof BigDecimal) {
            BigDecimal leftBigDecimal = (BigDecimal)NumberUtils.convertNumberToTargetClass(leftNumber, BigDecimal.class);
            BigDecimal rightBigDecimal = (BigDecimal)NumberUtils.convertNumberToTargetClass(rightNumber, BigDecimal.class);
            return leftBigDecimal.compareTo(rightBigDecimal) == 0;
         } else if (leftNumber instanceof Double || rightNumber instanceof Double) {
            return leftNumber.doubleValue() == rightNumber.doubleValue();
         } else if (leftNumber instanceof Float || rightNumber instanceof Float) {
            return leftNumber.floatValue() == rightNumber.floatValue();
         } else if (leftNumber instanceof BigInteger || rightNumber instanceof BigInteger) {
            BigInteger leftBigInteger = (BigInteger)NumberUtils.convertNumberToTargetClass(leftNumber, BigInteger.class);
            BigInteger rightBigInteger = (BigInteger)NumberUtils.convertNumberToTargetClass(rightNumber, BigInteger.class);
            return leftBigInteger.compareTo(rightBigInteger) == 0;
         } else if (leftNumber instanceof Long || rightNumber instanceof Long) {
            return leftNumber.longValue() == rightNumber.longValue();
         } else if (leftNumber instanceof Integer || rightNumber instanceof Integer) {
            return leftNumber.intValue() == rightNumber.intValue();
         } else if (leftNumber instanceof Short || rightNumber instanceof Short) {
            return leftNumber.shortValue() == rightNumber.shortValue();
         } else {
            return !(leftNumber instanceof Byte) && !(rightNumber instanceof Byte)
               ? leftNumber.doubleValue() == rightNumber.doubleValue()
               : leftNumber.byteValue() == rightNumber.byteValue();
         }
      } else if (left instanceof CharSequence && right instanceof CharSequence) {
         return left.toString().equals(right.toString());
      } else if (left instanceof Boolean && right instanceof Boolean) {
         return left.equals(right);
      } else if (ObjectUtils.nullSafeEquals(left, right)) {
         return true;
      } else {
         return context.getTypeComparator().canCompare(left, right) ? context.getTypeComparator().compare(left, right) == 0 : false;
      }
   }

   protected static final class DescriptorComparison {
      static final Operator.DescriptorComparison NOT_NUMBERS = new Operator.DescriptorComparison(false, false, ' ');
      static final Operator.DescriptorComparison INCOMPATIBLE_NUMBERS = new Operator.DescriptorComparison(true, false, ' ');
      final boolean areNumbers;
      final boolean areCompatible;
      final char compatibleType;

      private DescriptorComparison(boolean areNumbers, boolean areCompatible, char compatibleType) {
         this.areNumbers = areNumbers;
         this.areCompatible = areCompatible;
         this.compatibleType = compatibleType;
      }

      public static Operator.DescriptorComparison checkNumericCompatibility(
         @Nullable String leftDeclaredDescriptor,
         @Nullable String rightDeclaredDescriptor,
         @Nullable String leftActualDescriptor,
         @Nullable String rightActualDescriptor
      ) {
         String ld = leftDeclaredDescriptor;
         String rd = rightDeclaredDescriptor;
         boolean leftNumeric = CodeFlow.isPrimitiveOrUnboxableSupportedNumberOrBoolean(ld);
         boolean rightNumeric = CodeFlow.isPrimitiveOrUnboxableSupportedNumberOrBoolean(rd);
         if (!leftNumeric && !Objects.equals(ld, leftActualDescriptor)) {
            ld = leftActualDescriptor;
            leftNumeric = CodeFlow.isPrimitiveOrUnboxableSupportedNumberOrBoolean(ld);
         }

         if (!rightNumeric && !Objects.equals(rd, rightActualDescriptor)) {
            rd = rightActualDescriptor;
            rightNumeric = CodeFlow.isPrimitiveOrUnboxableSupportedNumberOrBoolean(rd);
         }

         if (!leftNumeric || !rightNumeric) {
            return NOT_NUMBERS;
         } else {
            return CodeFlow.areBoxingCompatible(ld, rd)
               ? new Operator.DescriptorComparison(true, true, CodeFlow.toPrimitiveTargetDesc(ld))
               : INCOMPATIBLE_NUMBERS;
         }
      }
   }
}
