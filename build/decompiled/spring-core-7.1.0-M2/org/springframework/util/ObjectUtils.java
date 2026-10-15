package org.springframework.util;

import java.lang.reflect.Array;
import java.nio.charset.Charset;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collection;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.TimeZone;
import org.jspecify.annotations.Nullable;
import org.springframework.lang.Contract;

public abstract class ObjectUtils {
   private static final String EMPTY_STRING = "";
   private static final String NULL_STRING = "null";
   private static final String ARRAY_START = "{";
   private static final String ARRAY_END = "}";
   private static final String EMPTY_ARRAY = "{}";
   private static final String ARRAY_ELEMENT_SEPARATOR = ", ";
   private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
   private static final String NON_EMPTY_ARRAY = "{...}";
   private static final String COLLECTION = "[...]";
   private static final String MAP = "{...}";

   public static boolean isCheckedException(Throwable ex) {
      return !(ex instanceof RuntimeException) && !(ex instanceof Error);
   }

   public static boolean isCompatibleWithThrowsClause(Throwable ex, Class<?> @Nullable ... declaredExceptions) {
      if (!isCheckedException(ex)) {
         return true;
      }

      if (declaredExceptions != null) {
         for (Class<?> declaredException : declaredExceptions) {
            if (declaredException.isInstance(ex)) {
               return true;
            }
         }
      }

      return false;
   }

   @Contract("null -> false")
   public static boolean isArray(@Nullable Object obj) {
      return obj != null && obj.getClass().isArray();
   }

   @Contract("null -> true")
   public static boolean isEmpty(@Nullable Object @Nullable [] array) {
      return array == null || array.length == 0;
   }

   @Contract("null -> true")
   public static boolean isEmpty(@Nullable Object obj) {
      if (obj == null) {
         return true;
      } else if (obj instanceof Optional<?> optional) {
         return optional.isEmpty();
      } else if (obj instanceof CharSequence charSequence) {
         return charSequence.isEmpty();
      } else if (obj.getClass().isArray()) {
         return Array.getLength(obj) == 0;
      } else if (obj instanceof Collection<?> collection) {
         return collection.isEmpty();
      } else {
         return obj instanceof Map<?, ?> map ? map.isEmpty() : false;
      }
   }

   @Contract("null -> null")
   public static @Nullable Object unwrapOptional(@Nullable Object obj) {
      if (obj instanceof Optional<?> optional) {
         Object result = optional.orElse(null);
         Assert.isTrue(!(result instanceof Optional), "Multi-level Optional usage not supported");
         return result;
      } else {
         return obj;
      }
   }

   @Contract("null, _ -> false")
   public static boolean containsElement(@Nullable Object @Nullable [] array, @Nullable Object element) {
      if (array == null) {
         return false;
      }

      for (Object arrayEle : array) {
         if (nullSafeEquals(arrayEle, element)) {
            return true;
         }
      }

      return false;
   }

   public static boolean containsConstant(Enum<?>[] enumValues, String constant) {
      return containsConstant(enumValues, constant, false);
   }

   public static boolean containsConstant(Enum<?>[] enumValues, String constant, boolean caseSensitive) {
      for (Enum<?> candidate : enumValues) {
         if (caseSensitive ? candidate.toString().equals(constant) : candidate.toString().equalsIgnoreCase(constant)) {
            return true;
         }
      }

      return false;
   }

   public static <E extends Enum<?>> E caseInsensitiveValueOf(E[] enumValues, String constant) {
      for (E candidate : enumValues) {
         if (candidate.toString().equalsIgnoreCase(constant)) {
            return candidate;
         }
      }

      throw new IllegalArgumentException("Constant [" + constant + "] does not exist in enum type " + enumValues.getClass().componentType().getName());
   }

   public static <A, O extends A> A[] addObjectToArray(A @Nullable [] array, O obj) {
      return (A[])addObjectToArray(array, obj, array != null ? array.length : 0);
   }

   public static <A, O extends A> A[] addObjectToArray(A @Nullable [] array, O obj, int position) {
      Class<?> componentType = Object.class;
      if (array != null) {
         componentType = array.getClass().componentType();
      } else if (obj != null) {
         componentType = obj.getClass();
      }

      int newArrayLength = array != null ? array.length + 1 : 1;
      A[] newArray = (A[])Array.newInstance(componentType, newArrayLength);
      if (array != null) {
         System.arraycopy(array, 0, newArray, 0, position);
         System.arraycopy(array, position, newArray, position + 1, array.length - position);
      }

      newArray[position] = (A)obj;
      return newArray;
   }

   public static Object[] toObjectArray(@Nullable Object source) {
      if (source instanceof Object[] objects) {
         return objects;
      } else {
         if (source == null) {
            return EMPTY_OBJECT_ARRAY;
         }

         if (!source.getClass().isArray()) {
            throw new IllegalArgumentException("Source is not an array: " + source);
         }

         int length = Array.getLength(source);
         if (length == 0) {
            return EMPTY_OBJECT_ARRAY;
         }

         Class<?> wrapperType = Array.get(source, 0).getClass();
         Object[] newArray = (Object[])Array.newInstance(wrapperType, length);

         for (int i = 0; i < length; i++) {
            newArray[i] = Array.get(source, i);
         }

         return newArray;
      }
   }

   @Contract("null, null -> true; null, _ -> false; _, null -> false")
   public static boolean nullSafeEquals(@Nullable Object o1, @Nullable Object o2) {
      if (o1 == o2) {
         return true;
      } else if (o1 == null || o2 == null) {
         return false;
      } else if (o1.equals(o2)) {
         return true;
      } else {
         return o1.getClass().isArray() && o2.getClass().isArray() ? arrayEquals(o1, o2) : false;
      }
   }

   private static boolean arrayEquals(Object o1, Object o2) {
      if (o1 instanceof Object[] objects1 && o2 instanceof Object[] objects2) {
         return Arrays.equals(objects1, objects2);
      } else {
         Class<?> type1 = o1.getClass();
         Class<?> type2 = o2.getClass();
         if (type1 == boolean[].class && type2 == boolean[].class) {
            return Arrays.equals((boolean[])o1, (boolean[])o2);
         } else if (type1 == byte[].class && type2 == byte[].class) {
            return Arrays.equals((byte[])o1, (byte[])o2);
         } else if (type1 == char[].class && type2 == char[].class) {
            return Arrays.equals((char[])o1, (char[])o2);
         } else if (type1 == double[].class && type2 == double[].class) {
            return Arrays.equals((double[])o1, (double[])o2);
         } else if (type1 == float[].class && type2 == float[].class) {
            return Arrays.equals((float[])o1, (float[])o2);
         } else if (type1 == int[].class && type2 == int[].class) {
            return Arrays.equals((int[])o1, (int[])o2);
         } else if (type1 == long[].class && type2 == long[].class) {
            return Arrays.equals((long[])o1, (long[])o2);
         } else {
            return type1 == short[].class && type2 == short[].class ? Arrays.equals((short[])o1, (short[])o2) : false;
         }
      }
   }

   public static int nullSafeHash(@Nullable Object @Nullable ... elements) {
      if (elements == null) {
         return 0;
      }

      int result = 1;

      for (Object element : elements) {
         result = 31 * result + nullSafeHashCode(element);
      }

      return result;
   }

   public static int nullSafeHashCode(@Nullable Object obj) {
      if (obj == null) {
         return 0;
      }

      if (obj.getClass().isArray()) {
         if (obj instanceof Object[] objects) {
            return Arrays.hashCode(objects);
         }

         if (obj instanceof boolean[] booleans) {
            return Arrays.hashCode(booleans);
         }

         if (obj instanceof byte[] bytes) {
            return Arrays.hashCode(bytes);
         }

         if (obj instanceof char[] chars) {
            return Arrays.hashCode(chars);
         }

         if (obj instanceof double[] doubles) {
            return Arrays.hashCode(doubles);
         }

         if (obj instanceof float[] floats) {
            return Arrays.hashCode(floats);
         }

         if (obj instanceof int[] ints) {
            return Arrays.hashCode(ints);
         }

         if (obj instanceof long[] longs) {
            return Arrays.hashCode(longs);
         }

         if (obj instanceof short[] shorts) {
            return Arrays.hashCode(shorts);
         }
      }

      return obj.hashCode();
   }

   @Deprecated(since = "6.1")
   public static int nullSafeHashCode(Object @Nullable [] array) {
      return Arrays.hashCode(array);
   }

   @Deprecated(since = "6.1")
   public static int nullSafeHashCode(boolean @Nullable [] array) {
      return Arrays.hashCode(array);
   }

   @Deprecated(since = "6.1")
   public static int nullSafeHashCode(byte @Nullable [] array) {
      return Arrays.hashCode(array);
   }

   @Deprecated(since = "6.1")
   public static int nullSafeHashCode(char @Nullable [] array) {
      return Arrays.hashCode(array);
   }

   @Deprecated(since = "6.1")
   public static int nullSafeHashCode(double @Nullable [] array) {
      return Arrays.hashCode(array);
   }

   @Deprecated(since = "6.1")
   public static int nullSafeHashCode(float @Nullable [] array) {
      return Arrays.hashCode(array);
   }

   @Deprecated(since = "6.1")
   public static int nullSafeHashCode(int @Nullable [] array) {
      return Arrays.hashCode(array);
   }

   @Deprecated(since = "6.1")
   public static int nullSafeHashCode(long @Nullable [] array) {
      return Arrays.hashCode(array);
   }

   @Deprecated(since = "6.1")
   public static int nullSafeHashCode(short @Nullable [] array) {
      return Arrays.hashCode(array);
   }

   public static String identityToString(@Nullable Object obj) {
      return obj == null ? "" : obj.getClass().getName() + "@" + getIdentityHexString(obj);
   }

   public static String getIdentityHexString(Object obj) {
      return Integer.toHexString(System.identityHashCode(obj));
   }

   public static String getDisplayString(@Nullable Object obj) {
      return obj == null ? "" : nullSafeToString(obj);
   }

   public static String nullSafeClassName(@Nullable Object obj) {
      return obj != null ? obj.getClass().getName() : "null";
   }

   public static String nullSafeToString(@Nullable Object obj) {
      if (obj == null) {
         return "null";
      } else if (obj instanceof String string) {
         return string;
      } else if (obj instanceof Object[] objects) {
         return nullSafeToString(objects);
      } else if (obj instanceof boolean[] booleans) {
         return nullSafeToString(booleans);
      } else if (obj instanceof byte[] bytes) {
         return nullSafeToString(bytes);
      } else if (obj instanceof char[] chars) {
         return nullSafeToString(chars);
      } else if (obj instanceof double[] doubles) {
         return nullSafeToString(doubles);
      } else if (obj instanceof float[] floats) {
         return nullSafeToString(floats);
      } else if (obj instanceof int[] ints) {
         return nullSafeToString(ints);
      } else if (obj instanceof long[] longs) {
         return nullSafeToString(longs);
      } else if (obj instanceof short[] shorts) {
         return nullSafeToString(shorts);
      } else {
         String str = obj.toString();
         return str != null ? str : "";
      }
   }

   public static String nullSafeToString(@Nullable Object @Nullable [] array) {
      if (array == null) {
         return "null";
      }

      int length = array.length;
      if (length == 0) {
         return "{}";
      }

      StringJoiner stringJoiner = new StringJoiner(", ", "{", "}");

      for (Object o : array) {
         stringJoiner.add(String.valueOf(o));
      }

      return stringJoiner.toString();
   }

   public static String nullSafeToString(boolean @Nullable [] array) {
      if (array == null) {
         return "null";
      }

      int length = array.length;
      if (length == 0) {
         return "{}";
      }

      StringJoiner stringJoiner = new StringJoiner(", ", "{", "}");

      for (boolean b : array) {
         stringJoiner.add(String.valueOf(b));
      }

      return stringJoiner.toString();
   }

   public static String nullSafeToString(byte @Nullable [] array) {
      if (array == null) {
         return "null";
      } else {
         return array.length == 0 ? "{}" : "{" + HexFormat.of().formatHex(array) + "}";
      }
   }

   public static String nullSafeToString(char @Nullable [] array) {
      if (array == null) {
         return "null";
      }

      if (array.length == 0) {
         return "{}";
      }

      StringJoiner stringJoiner = new StringJoiner(", ", "{", "}");

      for (char c : array) {
         stringJoiner.add("'" + c + "'");
      }

      return stringJoiner.toString();
   }

   public static String nullSafeToString(double @Nullable [] array) {
      if (array == null) {
         return "null";
      }

      if (array.length == 0) {
         return "{}";
      }

      StringJoiner stringJoiner = new StringJoiner(", ", "{", "}");

      for (double d : array) {
         stringJoiner.add(String.valueOf(d));
      }

      return stringJoiner.toString();
   }

   public static String nullSafeToString(float @Nullable [] array) {
      if (array == null) {
         return "null";
      }

      if (array.length == 0) {
         return "{}";
      }

      StringJoiner stringJoiner = new StringJoiner(", ", "{", "}");

      for (float f : array) {
         stringJoiner.add(String.valueOf(f));
      }

      return stringJoiner.toString();
   }

   public static String nullSafeToString(int @Nullable [] array) {
      if (array == null) {
         return "null";
      }

      if (array.length == 0) {
         return "{}";
      }

      StringJoiner stringJoiner = new StringJoiner(", ", "{", "}");

      for (int i : array) {
         stringJoiner.add(String.valueOf(i));
      }

      return stringJoiner.toString();
   }

   public static String nullSafeToString(long @Nullable [] array) {
      if (array == null) {
         return "null";
      }

      int length = array.length;
      if (length == 0) {
         return "{}";
      }

      StringJoiner stringJoiner = new StringJoiner(", ", "{", "}");

      for (long l : array) {
         stringJoiner.add(String.valueOf(l));
      }

      return stringJoiner.toString();
   }

   public static String nullSafeToString(short @Nullable [] array) {
      if (array == null) {
         return "null";
      }

      if (array.length == 0) {
         return "{}";
      }

      StringJoiner stringJoiner = new StringJoiner(", ", "{", "}");

      for (short s : array) {
         stringJoiner.add(String.valueOf(s));
      }

      return stringJoiner.toString();
   }

   public static String nullSafeConciseToString(@Nullable Object obj) {
      if (obj == null) {
         return "null";
      } else if (obj instanceof Optional<?> optional) {
         return optional.isEmpty() ? "Optional.empty" : "Optional[%s]".formatted(nullSafeConciseToString(optional.get()));
      } else if (obj.getClass().isArray()) {
         return Array.getLength(obj) == 0 ? "{}" : "{...}";
      } else if (obj instanceof Collection) {
         return "[...]";
      } else if (obj instanceof Map) {
         return "{...}";
      } else if (obj instanceof Class<?> clazz) {
         return clazz.getName();
      } else if (obj instanceof Charset charset) {
         return charset.name();
      } else if (obj instanceof TimeZone timeZone) {
         return timeZone.getID();
      } else if (obj instanceof ZoneId zoneId) {
         return zoneId.getId();
      } else if (obj instanceof CharSequence charSequence) {
         return StringUtils.truncate(charSequence);
      } else {
         Class<?> type = obj.getClass();
         if (ClassUtils.isSimpleValueType(type)) {
            String str = obj.toString();
            if (str != null) {
               return StringUtils.truncate(str);
            }
         }

         return type.getTypeName() + "@" + getIdentityHexString(obj);
      }
   }
}
