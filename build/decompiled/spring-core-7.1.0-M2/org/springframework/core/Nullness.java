package org.springframework.core;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Objects;
import java.util.function.Predicate;
import kotlin.jvm.JvmClassMappingKt;
import kotlin.reflect.KClass;
import kotlin.reflect.KFunction;
import kotlin.reflect.KParameter;
import kotlin.reflect.KProperty;
import kotlin.reflect.KType;
import kotlin.reflect.KParameter.Kind;
import kotlin.reflect.full.KClasses;
import kotlin.reflect.jvm.ReflectJvmMapping;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

public enum Nullness {
   UNSPECIFIED,
   NULLABLE,
   NON_NULL;

   private static final boolean KOTLIN_REFLECT_PRESENT = KotlinDetector.isKotlinReflectPresent();

   public static Nullness forMethodReturnType(Method method) {
      if (KOTLIN_REFLECT_PRESENT && KotlinDetector.isKotlinType(method.getDeclaringClass())) {
         return Nullness.KotlinDelegate.forMethodReturnType(method);
      } else {
         return hasNullableAnnotation(method) ? NULLABLE : jSpecifyNullness(method, method.getDeclaringClass(), method.getAnnotatedReturnType());
      }
   }

   public static Nullness forParameter(Parameter parameter) {
      if (KOTLIN_REFLECT_PRESENT && KotlinDetector.isKotlinType(parameter.getDeclaringExecutable().getDeclaringClass())) {
         MethodParameter methodParameter = MethodParameter.forParameter(parameter);
         return Nullness.KotlinDelegate.forParameter(methodParameter.getExecutable(), methodParameter.getParameterIndex());
      } else {
         Executable executable = parameter.getDeclaringExecutable();
         return hasNullableAnnotation(parameter) ? NULLABLE : jSpecifyNullness(executable, executable.getDeclaringClass(), parameter.getAnnotatedType());
      }
   }

   public static Nullness forMethodParameter(MethodParameter methodParameter) {
      return methodParameter.getParameterIndex() < 0
         ? forMethodReturnType(Objects.requireNonNull(methodParameter.getMethod()))
         : forParameter(methodParameter.getParameter());
   }

   public static Nullness forField(Field field) {
      if (KOTLIN_REFLECT_PRESENT && KotlinDetector.isKotlinType(field.getDeclaringClass())) {
         return Nullness.KotlinDelegate.forField(field);
      } else {
         return hasNullableAnnotation(field) ? NULLABLE : jSpecifyNullness(field, field.getDeclaringClass(), field.getAnnotatedType());
      }
   }

   private static boolean hasNullableAnnotation(AnnotatedElement element) {
      for (Annotation annotation : element.getDeclaredAnnotations()) {
         if ("Nullable".equals(annotation.annotationType().getSimpleName())) {
            return true;
         }
      }

      return false;
   }

   private static Nullness jSpecifyNullness(AnnotatedElement annotatedElement, Class<?> declaringClass, AnnotatedType annotatedType) {
      if (annotatedType.getType() instanceof Class<?> clazz && clazz.isPrimitive()) {
         return clazz != void.class ? NON_NULL : UNSPECIFIED;
      } else {
         if (annotatedType.isAnnotationPresent(Nullable.class)) {
            return NULLABLE;
         }

         if (annotatedType.isAnnotationPresent(NonNull.class)) {
            return NON_NULL;
         }

         Nullness nullness = UNSPECIFIED;
         Package declaringPackage = declaringClass.getPackage();
         if (declaringPackage.isAnnotationPresent(NullMarked.class)) {
            nullness = NON_NULL;
         }

         if (declaringClass.isAnnotationPresent(NullMarked.class)) {
            nullness = NON_NULL;
         } else if (declaringClass.isAnnotationPresent(NullUnmarked.class)) {
            nullness = UNSPECIFIED;
         }

         if (annotatedElement.isAnnotationPresent(NullMarked.class)) {
            nullness = NON_NULL;
         } else if (annotatedElement.isAnnotationPresent(NullUnmarked.class)) {
            nullness = UNSPECIFIED;
         }

         return nullness;
      }
   }

   private static class KotlinDelegate {
      public static Nullness forMethodReturnType(Method method) {
         KFunction<?> function = ReflectJvmMapping.getKotlinFunction(method);
         if (function == null) {
            String methodName = method.getName();
            if (methodName.startsWith("get")) {
               String propertyName = accessorToPropertyName(methodName);
               KClass<?> kClass = JvmClassMappingKt.getKotlinClass(method.getDeclaringClass());

               for (KProperty<?> property : KClasses.getMemberProperties(kClass)) {
                  if (property.getName().equals(propertyName)) {
                     return property.getReturnType().isMarkedNullable() ? Nullness.NULLABLE : Nullness.NON_NULL;
                  }
               }
            }
         } else {
            KType type = function.getReturnType();
            if (ReflectJvmMapping.getJavaType(type) != void.class) {
               return type.isMarkedNullable() ? Nullness.NULLABLE : Nullness.NON_NULL;
            }
         }

         return Nullness.UNSPECIFIED;
      }

      public static Nullness forParameter(Executable executable, int parameterIndex) {
         KFunction<?> function;
         Predicate<KParameter> predicate;
         if (executable instanceof Method method) {
            function = ReflectJvmMapping.getKotlinFunction(method);
            predicate = p -> Kind.VALUE.equals(p.getKind());
         } else {
            function = ReflectJvmMapping.getKotlinFunction((Constructor)executable);
            predicate = p -> Kind.VALUE.equals(p.getKind()) || Kind.INSTANCE.equals(p.getKind());
         }

         if (function == null) {
            String methodName = executable.getName();
            if (methodName.startsWith("set")) {
               String propertyName = accessorToPropertyName(methodName);
               KClass<?> kClass = JvmClassMappingKt.getKotlinClass(executable.getDeclaringClass());

               for (KProperty<?> property : KClasses.getMemberProperties(kClass)) {
                  if (property.getName().equals(propertyName)) {
                     return property.getReturnType().isMarkedNullable() ? Nullness.NULLABLE : Nullness.NON_NULL;
                  }
               }
            }
         } else {
            int i = 0;

            for (KParameter kParameter : function.getParameters()) {
               if (predicate.test(kParameter) && parameterIndex == i++) {
                  return kParameter.getType().isMarkedNullable() ? Nullness.NULLABLE : Nullness.NON_NULL;
               }
            }
         }

         return Nullness.UNSPECIFIED;
      }

      public static Nullness forField(Field field) {
         KProperty<?> property = ReflectJvmMapping.getKotlinProperty(field);
         if (property != null) {
            return property.getReturnType().isMarkedNullable() ? Nullness.NULLABLE : Nullness.NON_NULL;
         } else {
            return Nullness.UNSPECIFIED;
         }
      }

      private static String accessorToPropertyName(String method) {
         char[] methodNameChars = method.toCharArray();
         methodNameChars[3] = Character.toLowerCase(methodNameChars[3]);
         return new String(methodNameChars, 3, methodNameChars.length - 3);
      }
   }
}
