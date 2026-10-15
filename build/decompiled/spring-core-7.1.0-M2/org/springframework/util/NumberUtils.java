package org.springframework.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public abstract class NumberUtils {
   private static final BigInteger LONG_MIN = BigInteger.valueOf(Long.MIN_VALUE);
   private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
   public static final Set<Class<?>> STANDARD_NUMBER_TYPES = Set.of(
      Byte.class, Short.class, Integer.class, Long.class, BigInteger.class, Float.class, Double.class, BigDecimal.class
   );

   public static <T extends Number> T convertNumberToTargetClass(Number number, Class<T> targetClass) throws IllegalArgumentException {
      Assert.notNull(number, "Number must not be null");
      Assert.notNull(targetClass, "Target class must not be null");
      if (targetClass.isInstance(number)) {
         return (T)number;
      }

      if (Byte.class == targetClass) {
         long value = checkedLongValue(number, targetClass);
         if (value < -128L || value > 127L) {
            raiseOverflowException(number, targetClass);
         }

         return (T)number.byteValue();
      } else if (Short.class == targetClass) {
         long value = checkedLongValue(number, targetClass);
         if (value < -32768L || value > 32767L) {
            raiseOverflowException(number, targetClass);
         }

         return (T)number.shortValue();
      } else if (Integer.class != targetClass) {
         if (Long.class == targetClass) {
            long value = checkedLongValue(number, targetClass);
            return (T)value;
         } else if (BigInteger.class == targetClass) {
            return (T)(number instanceof BigDecimal bigDecimal ? bigDecimal.toBigInteger() : BigInteger.valueOf(number.longValue()));
         } else if (Float.class == targetClass) {
            return (T)number.floatValue();
         } else if (Double.class == targetClass) {
            return (T)number.doubleValue();
         } else if (BigDecimal.class == targetClass) {
            return (T)(new BigDecimal(number.toString()));
         } else {
            throw new IllegalArgumentException(
               "Could not convert number ["
                  + number
                  + "] of type ["
                  + number.getClass().getName()
                  + "] to unsupported target class ["
                  + targetClass.getName()
                  + "]"
            );
         }
      } else {
         long value = checkedLongValue(number, targetClass);
         if (value < -2147483648L || value > 2147483647L) {
            raiseOverflowException(number, targetClass);
         }

         return (T)number.intValue();
      }
   }

   private static long checkedLongValue(Number number, Class<? extends Number> targetClass) {
      BigInteger bigInt = null;
      if (number instanceof BigInteger bigInteger) {
         bigInt = bigInteger;
      } else if (number instanceof BigDecimal bigDecimal) {
         bigInt = bigDecimal.toBigInteger();
      }

      if (bigInt != null && (bigInt.compareTo(LONG_MIN) < 0 || bigInt.compareTo(LONG_MAX) > 0)) {
         raiseOverflowException(number, targetClass);
      }

      return number.longValue();
   }

   private static void raiseOverflowException(Number number, Class<?> targetClass) {
      throw new IllegalArgumentException(
         "Could not convert number [" + number + "] of type [" + number.getClass().getName() + "] to target class [" + targetClass.getName() + "]: overflow"
      );
   }

   public static <T extends Number> T parseNumber(String text, Class<T> targetClass) {
      Assert.notNull(text, "Text must not be null");
      Assert.notNull(targetClass, "Target class must not be null");
      String trimmed = StringUtils.trimAllWhitespace(text);
      if (Byte.class == targetClass) {
         return (T)(isHexNumber(trimmed) ? Byte.decode(trimmed) : Byte.valueOf(trimmed));
      } else if (Short.class == targetClass) {
         return (T)(isHexNumber(trimmed) ? Short.decode(trimmed) : Short.valueOf(trimmed));
      } else if (Integer.class == targetClass) {
         return (T)(isHexNumber(trimmed) ? Integer.decode(trimmed) : Integer.valueOf(trimmed));
      } else if (Long.class == targetClass) {
         return (T)(isHexNumber(trimmed) ? Long.decode(trimmed) : Long.valueOf(trimmed));
      } else if (BigInteger.class == targetClass) {
         return (T)(isHexNumber(trimmed) ? decodeBigInteger(trimmed) : new BigInteger(trimmed));
      } else if (Float.class == targetClass) {
         return (T)Float.valueOf(trimmed);
      } else if (Double.class == targetClass) {
         return (T)Double.valueOf(trimmed);
      } else if (BigDecimal.class != targetClass && Number.class != targetClass) {
         throw new IllegalArgumentException("Cannot convert String [" + text + "] to target class [" + targetClass.getName() + "]");
      } else {
         return (T)(new BigDecimal(trimmed));
      }
   }

   public static <T extends Number> T parseNumber(String text, Class<T> targetClass, @Nullable NumberFormat numberFormat) {
      if (numberFormat == null) {
         return parseNumber(text, targetClass);
      }

      Assert.notNull(text, "Text must not be null");
      Assert.notNull(targetClass, "Target class must not be null");
      DecimalFormat decimalFormat = null;
      boolean resetBigDecimal = false;
      if (numberFormat instanceof DecimalFormat dc) {
         decimalFormat = dc;
         if (BigDecimal.class == targetClass && !decimalFormat.isParseBigDecimal()) {
            decimalFormat.setParseBigDecimal(true);
            resetBigDecimal = true;
         }
      }

      try {
         Number number = numberFormat.parse(StringUtils.trimAllWhitespace(text));
         return convertNumberToTargetClass(number, targetClass);
      } catch (ParseException ex) {
         throw new IllegalArgumentException("Could not parse number: " + ex.getMessage());
      } finally {
         if (resetBigDecimal) {
            decimalFormat.setParseBigDecimal(false);
         }
      }
   }

   private static boolean isHexNumber(String value) {
      int index = value.startsWith("-") ? 1 : 0;
      return value.startsWith("0x", index) || value.startsWith("0X", index) || value.startsWith("#", index);
   }

   private static BigInteger decodeBigInteger(String value) {
      int radix = 10;
      int index = 0;
      boolean negative = false;
      if (value.startsWith("-")) {
         negative = true;
         index++;
      }

      if (value.startsWith("0x", index) || value.startsWith("0X", index)) {
         index += 2;
         radix = 16;
      } else if (value.startsWith("#", index)) {
         index++;
         radix = 16;
      } else if (value.startsWith("0", index) && value.length() > 1 + index) {
         index++;
         radix = 8;
      }

      BigInteger result = new BigInteger(value.substring(index), radix);
      return negative ? result.negate() : result;
   }
}
