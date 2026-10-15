package org.springframework.util;

import java.util.Collection;
import java.util.Map;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.lang.Contract;

public abstract class Assert {
   @Contract("false, _ -> fail")
   public static void state(boolean expression, String message) {
      if (!expression) {
         throw new IllegalStateException(message);
      }
   }

   @Contract("false, _ -> fail")
   public static void state(boolean expression, Supplier<String> messageSupplier) {
      if (!expression) {
         throw new IllegalStateException(nullSafeGet(messageSupplier));
      }
   }

   @Contract("false, _ -> fail")
   public static void isTrue(boolean expression, String message) {
      if (!expression) {
         throw new IllegalArgumentException(message);
      }
   }

   @Contract("false, _ -> fail")
   public static void isTrue(boolean expression, Supplier<String> messageSupplier) {
      if (!expression) {
         throw new IllegalArgumentException(nullSafeGet(messageSupplier));
      }
   }

   @Contract("!null, _ -> fail")
   public static void isNull(@Nullable Object object, String message) {
      if (object != null) {
         throw new IllegalArgumentException(message);
      }
   }

   @Contract("!null, _ -> fail")
   public static void isNull(@Nullable Object object, Supplier<String> messageSupplier) {
      if (object != null) {
         throw new IllegalArgumentException(nullSafeGet(messageSupplier));
      }
   }

   @Contract("null, _ -> fail")
   public static void notNull(@Nullable Object object, String message) {
      if (object == null) {
         throw new IllegalArgumentException(message);
      }
   }

   @Contract("null, _ -> fail")
   public static void notNull(@Nullable Object object, Supplier<String> messageSupplier) {
      if (object == null) {
         throw new IllegalArgumentException(nullSafeGet(messageSupplier));
      }
   }

   @Contract("null, _ -> fail")
   public static void hasLength(@Nullable String text, String message) {
      if (!StringUtils.hasLength(text)) {
         throw new IllegalArgumentException(message);
      }
   }

   @Contract("null, _ -> fail")
   public static void hasLength(@Nullable String text, Supplier<String> messageSupplier) {
      if (!StringUtils.hasLength(text)) {
         throw new IllegalArgumentException(nullSafeGet(messageSupplier));
      }
   }

   @Contract("null, _ -> fail")
   public static void hasText(@Nullable String text, String message) {
      if (!StringUtils.hasText(text)) {
         throw new IllegalArgumentException(message);
      }
   }

   @Contract("null, _ -> fail")
   public static void hasText(@Nullable String text, Supplier<String> messageSupplier) {
      if (!StringUtils.hasText(text)) {
         throw new IllegalArgumentException(nullSafeGet(messageSupplier));
      }
   }

   public static void doesNotContain(@Nullable String textToSearch, String substring, String message) {
      if (StringUtils.hasLength(textToSearch) && StringUtils.hasLength(substring) && textToSearch.contains(substring)) {
         throw new IllegalArgumentException(message);
      }
   }

   public static void doesNotContain(@Nullable String textToSearch, String substring, Supplier<String> messageSupplier) {
      if (StringUtils.hasLength(textToSearch) && StringUtils.hasLength(substring) && textToSearch.contains(substring)) {
         throw new IllegalArgumentException(nullSafeGet(messageSupplier));
      }
   }

   @Contract("null, _ -> fail")
   public static void notEmpty(@Nullable Object @Nullable [] array, String message) {
      if (ObjectUtils.isEmpty(array)) {
         throw new IllegalArgumentException(message);
      }
   }

   @Contract("null, _ -> fail")
   public static void notEmpty(Object @Nullable [] array, Supplier<String> messageSupplier) {
      if (ObjectUtils.isEmpty(array)) {
         throw new IllegalArgumentException(nullSafeGet(messageSupplier));
      }
   }

   public static void noNullElements(@Nullable Object @Nullable [] array, String message) {
      if (array != null) {
         for (Object element : array) {
            if (element == null) {
               throw new IllegalArgumentException(message);
            }
         }
      }
   }

   public static void noNullElements(@Nullable Object @Nullable [] array, Supplier<String> messageSupplier) {
      if (array != null) {
         for (Object element : array) {
            if (element == null) {
               throw new IllegalArgumentException(nullSafeGet(messageSupplier));
            }
         }
      }
   }

   @Contract("null, _ -> fail")
   public static void notEmpty(@Nullable Collection<?> collection, String message) {
      if (CollectionUtils.isEmpty((Collection<? extends Object>)collection)) {
         throw new IllegalArgumentException(message);
      }
   }

   @Contract("null, _ -> fail")
   public static void notEmpty(@Nullable Collection<?> collection, Supplier<String> messageSupplier) {
      if (CollectionUtils.isEmpty((Collection<? extends Object>)collection)) {
         throw new IllegalArgumentException(nullSafeGet(messageSupplier));
      }
   }

   public static void noNullElements(@Nullable Collection<? extends @Nullable Object> collection, String message) {
      if (collection != null) {
         for (Object element : collection) {
            if (element == null) {
               throw new IllegalArgumentException(message);
            }
         }
      }
   }

   public static void noNullElements(@Nullable Collection<? extends @Nullable Object> collection, Supplier<String> messageSupplier) {
      if (collection != null) {
         for (Object element : collection) {
            if (element == null) {
               throw new IllegalArgumentException(nullSafeGet(messageSupplier));
            }
         }
      }
   }

   @Contract("null, _ -> fail")
   public static void notEmpty(@Nullable Map<?, ?> map, String message) {
      if (CollectionUtils.isEmpty((Map<?, ? extends Object>)map)) {
         throw new IllegalArgumentException(message);
      }
   }

   @Contract("null, _ -> fail")
   public static void notEmpty(@Nullable Map<?, ?> map, Supplier<String> messageSupplier) {
      if (CollectionUtils.isEmpty((Map<?, ? extends Object>)map)) {
         throw new IllegalArgumentException(nullSafeGet(messageSupplier));
      }
   }

   @Contract("_, null, _ -> fail")
   public static void isInstanceOf(Class<?> type, @Nullable Object obj, String message) {
      notNull(type, "Type to check against must not be null");
      if (!type.isInstance(obj)) {
         instanceCheckFailed(type, obj, message);
      }
   }

   @Contract("_, null, _ -> fail")
   public static void isInstanceOf(Class<?> type, @Nullable Object obj, Supplier<String> messageSupplier) {
      notNull(type, "Type to check against must not be null");
      if (!type.isInstance(obj)) {
         instanceCheckFailed(type, obj, nullSafeGet(messageSupplier));
      }
   }

   @Contract("_, null -> fail")
   public static void isInstanceOf(Class<?> type, @Nullable Object obj) {
      isInstanceOf(type, obj, "");
   }

   @Contract("_, null, _ -> fail")
   public static void isAssignable(Class<?> superType, @Nullable Class<?> subType, String message) {
      notNull(superType, "Supertype to check against must not be null");
      if (subType == null || !superType.isAssignableFrom(subType)) {
         assignableCheckFailed(superType, subType, message);
      }
   }

   @Contract("_, null, _ -> fail")
   public static void isAssignable(Class<?> superType, @Nullable Class<?> subType, Supplier<String> messageSupplier) {
      notNull(superType, "Supertype to check against must not be null");
      if (subType == null || !superType.isAssignableFrom(subType)) {
         assignableCheckFailed(superType, subType, nullSafeGet(messageSupplier));
      }
   }

   @Contract("_, null -> fail")
   public static void isAssignable(Class<?> superType, @Nullable Class<?> subType) {
      isAssignable(superType, subType, "");
   }

   private static void instanceCheckFailed(Class<?> type, @Nullable Object obj, @Nullable String msg) {
      String className = obj != null ? obj.getClass().getName() : "null";
      String result = "";
      boolean defaultMessage = true;
      if (StringUtils.hasLength(msg)) {
         if (endsWithSeparator(msg)) {
            result = msg + " ";
         } else {
            result = messageWithTypeName(msg, className);
            defaultMessage = false;
         }
      }

      if (defaultMessage) {
         result = result + "Object of class [" + className + "] must be an instance of " + type;
      }

      throw new IllegalArgumentException(result);
   }

   private static void assignableCheckFailed(Class<?> superType, @Nullable Class<?> subType, @Nullable String msg) {
      String result = "";
      boolean defaultMessage = true;
      if (StringUtils.hasLength(msg)) {
         if (endsWithSeparator(msg)) {
            result = msg + " ";
         } else {
            result = messageWithTypeName(msg, subType);
            defaultMessage = false;
         }
      }

      if (defaultMessage) {
         result = result + subType + " is not assignable to " + superType;
      }

      throw new IllegalArgumentException(result);
   }

   private static boolean endsWithSeparator(String msg) {
      return msg.endsWith(":") || msg.endsWith(";") || msg.endsWith(",") || msg.endsWith(".");
   }

   private static String messageWithTypeName(String msg, @Nullable Object typeName) {
      return msg + (msg.endsWith(" ") ? "" : ": ") + typeName;
   }

   private static @Nullable String nullSafeGet(@Nullable Supplier<String> messageSupplier) {
      return messageSupplier != null ? messageSupplier.get() : null;
   }
}
