package org.springframework.core;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ClassUtils;

public abstract class KotlinDetector {
   private static final @Nullable Class<? extends Annotation> KOTLIN_METADATA;
   private static final @Nullable Class<? extends Annotation> KOTLIN_JVM_INLINE;
   private static final @Nullable Class<? extends Annotation> KOTLIN_SERIALIZABLE;
   private static final @Nullable Class<?> KOTLIN_COROUTINE_CONTINUATION;
   private static final boolean KOTLIN_PRESENT;
   private static final boolean KOTLIN_REFLECT_PRESENT;

   public static boolean isKotlinPresent() {
      return KOTLIN_PRESENT;
   }

   public static boolean isKotlinReflectPresent() {
      return KOTLIN_REFLECT_PRESENT;
   }

   public static boolean isKotlinType(Class<?> clazz) {
      return KOTLIN_PRESENT && clazz.getDeclaredAnnotation(KOTLIN_METADATA) != null;
   }

   public static boolean isSuspendingFunction(Method method) {
      if (KOTLIN_COROUTINE_CONTINUATION == null) {
         return false;
      }

      int parameterCount = method.getParameterCount();
      return parameterCount > 0 && method.getParameterTypes()[parameterCount - 1] == KOTLIN_COROUTINE_CONTINUATION;
   }

   public static boolean isInlineClass(Class<?> clazz) {
      return KOTLIN_JVM_INLINE != null && clazz.getDeclaredAnnotation(KOTLIN_JVM_INLINE) != null;
   }

   public static boolean hasSerializableAnnotation(ResolvableType type) {
      Class<?> resolvedClass = type.resolve();
      if (KOTLIN_SERIALIZABLE != null && resolvedClass != null) {
         if (resolvedClass.isAnnotationPresent(KOTLIN_SERIALIZABLE)) {
            return true;
         }

         for (ResolvableType genericType : type.getGenerics()) {
            if (hasSerializableAnnotation(genericType)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   static {
      ClassLoader classLoader = KotlinDetector.class.getClassLoader();
      Class<?> metadata = null;
      Class<?> jvmInline = null;
      Class<?> serializable = null;
      Class<?> coroutineContinuation = null;

      try {
         metadata = ClassUtils.forName("kotlin.Metadata", classLoader);

         try {
            jvmInline = ClassUtils.forName("kotlin.jvm.JvmInline", classLoader);
         } catch (ClassNotFoundException var8) {
         }

         try {
            serializable = ClassUtils.forName("kotlinx.serialization.Serializable", classLoader);
         } catch (ClassNotFoundException var7) {
         }

         try {
            coroutineContinuation = ClassUtils.forName("kotlin.coroutines.Continuation", classLoader);
         } catch (ClassNotFoundException var6) {
         }
      } catch (ClassNotFoundException var9) {
      }

      KOTLIN_METADATA = (Class<? extends Annotation>)metadata;
      KOTLIN_PRESENT = KOTLIN_METADATA != null;
      KOTLIN_REFLECT_PRESENT = ClassUtils.isPresent("kotlin.reflect.full.KClasses", classLoader);
      KOTLIN_JVM_INLINE = (Class<? extends Annotation>)jvmInline;
      KOTLIN_SERIALIZABLE = (Class<? extends Annotation>)serializable;
      KOTLIN_COROUTINE_CONTINUATION = coroutineContinuation;
   }
}
