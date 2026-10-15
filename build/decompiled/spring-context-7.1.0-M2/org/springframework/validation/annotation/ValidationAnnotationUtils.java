package org.springframework.validation.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;

public abstract class ValidationAnnotationUtils {
   private static final Object[] EMPTY_OBJECT_ARRAY = new Object[0];
   private static final Class<?>[] EMPTY_CLASS_ARRAY = new Class[0];

   public static Object @Nullable [] determineValidationHints(Annotation ann) {
      if (ann instanceof Validated validated) {
         return validated.value();
      } else {
         Class<? extends Annotation> annotationType = ann.annotationType();
         if ("jakarta.validation.Valid".equals(annotationType.getName())) {
            return EMPTY_OBJECT_ARRAY;
         } else {
            Validated validatedAnn = (Validated)AnnotationUtils.findAnnotation(annotationType, Validated.class);
            if (validatedAnn != null) {
               return validatedAnn.value();
            } else {
               return annotationType.getSimpleName().startsWith("Valid") ? convertValidationHints(AnnotationUtils.getValue(ann)) : null;
            }
         }
      }
   }

   private static Object[] convertValidationHints(@Nullable Object hints) {
      if (hints == null) {
         return EMPTY_OBJECT_ARRAY;
      } else {
         return hints instanceof Object[] objectHints ? objectHints : new Object[]{hints};
      }
   }

   public static Class<?>[] determineValidationGroups(Object target, Method method) {
      Validated validatedAnn = (Validated)AnnotationUtils.findAnnotation(method, Validated.class);
      if (validatedAnn == null) {
         if (AopUtils.isAopProxy(target)) {
            for (Class<?> type : AopProxyUtils.proxiedUserInterfaces(target)) {
               validatedAnn = (Validated)AnnotationUtils.findAnnotation(type, Validated.class);
               if (validatedAnn != null) {
                  break;
               }
            }
         } else {
            validatedAnn = (Validated)AnnotationUtils.findAnnotation(target.getClass(), Validated.class);
         }
      }

      return validatedAnn != null ? validatedAnn.value() : EMPTY_CLASS_ARRAY;
   }
}
