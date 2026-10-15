package org.springframework.expression.spel.support;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.jspecify.annotations.Nullable;
import org.springframework.expression.TypeComparator;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.util.ClassUtils;
import org.springframework.util.NumberUtils;

public class StandardTypeComparator implements TypeComparator {
   static final StandardTypeComparator INSTANCE = new StandardTypeComparator();

   @Override
   public boolean canCompare(@Nullable Object left, @Nullable Object right) {
      if (left == null || right == null) {
         return true;
      } else if (left instanceof Number && right instanceof Number) {
         return true;
      } else if (left instanceof Comparable && right instanceof Comparable) {
         Class<?> ancestor = ClassUtils.determineCommonAncestor(left.getClass(), right.getClass());
         return ancestor != null && Comparable.class.isAssignableFrom(ancestor);
      } else {
         return false;
      }
   }

   @Override
   public int compare(@Nullable Object left, @Nullable Object right) throws SpelEvaluationException {
      if (left == null) {
         return right == null ? 0 : -1;
      }

      if (right == null) {
         return 1;
      }

      if (!(left instanceof Number leftNumber && right instanceof Number rightNumber)) {
         try {
            if (left instanceof Comparable comparable) {
               return comparable.compareTo(right);
            }
         } catch (ClassCastException ex) {
            throw new SpelEvaluationException(ex, SpelMessage.NOT_COMPARABLE, left.getClass(), right.getClass());
         }

         throw new SpelEvaluationException(SpelMessage.NOT_COMPARABLE, left.getClass(), right.getClass());
      } else if (leftNumber instanceof BigDecimal || rightNumber instanceof BigDecimal) {
         BigDecimal leftBigDecimal = (BigDecimal)NumberUtils.convertNumberToTargetClass(leftNumber, BigDecimal.class);
         BigDecimal rightBigDecimal = (BigDecimal)NumberUtils.convertNumberToTargetClass(rightNumber, BigDecimal.class);
         return leftBigDecimal.compareTo(rightBigDecimal);
      } else if (leftNumber instanceof Double || rightNumber instanceof Double) {
         return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue());
      } else if (leftNumber instanceof Float || rightNumber instanceof Float) {
         return Float.compare(leftNumber.floatValue(), rightNumber.floatValue());
      } else if (leftNumber instanceof BigInteger || rightNumber instanceof BigInteger) {
         BigInteger leftBigInteger = (BigInteger)NumberUtils.convertNumberToTargetClass(leftNumber, BigInteger.class);
         BigInteger rightBigInteger = (BigInteger)NumberUtils.convertNumberToTargetClass(rightNumber, BigInteger.class);
         return leftBigInteger.compareTo(rightBigInteger);
      } else if (leftNumber instanceof Long || rightNumber instanceof Long) {
         return Long.compare(leftNumber.longValue(), rightNumber.longValue());
      } else if (leftNumber instanceof Integer || rightNumber instanceof Integer) {
         return Integer.compare(leftNumber.intValue(), rightNumber.intValue());
      } else if (leftNumber instanceof Short || rightNumber instanceof Short) {
         return Short.compare(leftNumber.shortValue(), rightNumber.shortValue());
      } else {
         return !(leftNumber instanceof Byte) && !(rightNumber instanceof Byte)
            ? Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue())
            : Byte.compare(leftNumber.byteValue(), rightNumber.byteValue());
      }
   }
}
