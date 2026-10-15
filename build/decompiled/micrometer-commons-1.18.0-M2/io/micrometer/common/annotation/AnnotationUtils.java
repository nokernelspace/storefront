package io.micrometer.common.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

final class AnnotationUtils {
   private AnnotationUtils() {
   }

   static List<AnnotatedObject> findAnnotatedParameters(Class<? extends Annotation> annotationClazz, Method method, Object[] args) {
      Parameter[] parameters = method.getParameters();
      List<AnnotatedObject> result = new ArrayList<>();

      for (int i = 0; i < parameters.length; i++) {
         Parameter parameter = parameters[i];

         for (Annotation annotation : parameter.getAnnotationsByType(annotationClazz)) {
            result.add(new AnnotatedObject(annotation, args[i]));
         }
      }

      return result;
   }
}
