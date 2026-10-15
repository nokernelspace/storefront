package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ReflectionUtils;

final class AttributeMethods {
   private static final IntrospectionFailureLogger failureLogger = IntrospectionFailureLogger.WARN;
   static final AttributeMethods NONE = new AttributeMethods(null, new Method[0]);
   static final Map<Class<? extends Annotation>, AttributeMethods> cache = new ConcurrentReferenceHashMap<>();
   private static final Comparator<Method> methodComparator = (m1, m2) -> {
      if (m1 != null && m2 != null) {
         return m1.getName().compareTo(m2.getName());
      } else {
         return m1 != null ? -1 : 1;
      }
   };
   private final @Nullable Class<? extends Annotation> annotationType;
   private final Method[] attributeMethods;
   private final boolean[] canThrowTypeNotPresentException;
   private final boolean hasDefaultValueMethod;
   private final boolean hasNestedAnnotation;

   private AttributeMethods(@Nullable Class<? extends Annotation> annotationType, Method[] attributeMethods) {
      this.annotationType = annotationType;
      this.attributeMethods = attributeMethods;
      this.canThrowTypeNotPresentException = new boolean[attributeMethods.length];
      boolean foundDefaultValueMethod = false;
      boolean foundNestedAnnotation = false;

      for (int i = 0; i < attributeMethods.length; i++) {
         Method method = this.attributeMethods[i];
         Class<?> type = method.getReturnType();
         if (!foundDefaultValueMethod && method.getDefaultValue() != null) {
            foundDefaultValueMethod = true;
         }

         if (!foundNestedAnnotation && (type.isAnnotation() || type.isArray() && type.componentType().isAnnotation())) {
            foundNestedAnnotation = true;
         }

         ReflectionUtils.makeAccessible(method);
         this.canThrowTypeNotPresentException[i] = type == Class.class || type == Class[].class || type.isEnum();
      }

      this.hasDefaultValueMethod = foundDefaultValueMethod;
      this.hasNestedAnnotation = foundNestedAnnotation;
   }

   boolean canLoad(Annotation annotation, AnnotatedElement source) {
      this.assertAnnotation(annotation);

      for (int i = 0; i < this.size(); i++) {
         if (this.canThrowTypeNotPresentException(i)) {
            try {
               AnnotationUtils.invokeAnnotationMethod(this.get(i), annotation);
            } catch (IllegalStateException var5) {
            } catch (Throwable ex) {
               if (failureLogger.isEnabled()) {
                  failureLogger.log("Failed to introspect meta-annotation @" + annotation.annotationType().getSimpleName(), source, ex);
               }

               return false;
            }
         }
      }

      return true;
   }

   void validate(Annotation annotation) {
      this.assertAnnotation(annotation);

      for (int i = 0; i < this.size(); i++) {
         if (this.canThrowTypeNotPresentException(i)) {
            try {
               AnnotationUtils.invokeAnnotationMethod(this.get(i), annotation);
            } catch (IllegalStateException ex) {
               throw ex;
            } catch (Throwable ex) {
               throw new IllegalStateException(
                  "Could not obtain annotation attribute value for "
                     + this.get(i).getName()
                     + " declared on @"
                     + ClassUtils.getCanonicalName(annotation.annotationType()),
                  ex
               );
            }
         }
      }
   }

   private void assertAnnotation(Annotation annotation) {
      Assert.notNull(annotation, "Annotation must not be null");
      if (this.annotationType != null) {
         Assert.isInstanceOf(this.annotationType, annotation);
      }
   }

   @Nullable Method get(String name) {
      int index = this.indexOf(name);
      return index != -1 ? this.attributeMethods[index] : null;
   }

   Method get(int index) {
      return this.attributeMethods[index];
   }

   boolean canThrowTypeNotPresentException(int index) {
      return this.canThrowTypeNotPresentException[index];
   }

   int indexOf(String name) {
      for (int i = 0; i < this.attributeMethods.length; i++) {
         if (this.attributeMethods[i].getName().equals(name)) {
            return i;
         }
      }

      return -1;
   }

   int indexOf(Method attribute) {
      for (int i = 0; i < this.attributeMethods.length; i++) {
         if (this.attributeMethods[i].equals(attribute)) {
            return i;
         }
      }

      return -1;
   }

   int size() {
      return this.attributeMethods.length;
   }

   boolean hasDefaultValueMethod() {
      return this.hasDefaultValueMethod;
   }

   boolean hasNestedAnnotation() {
      return this.hasNestedAnnotation;
   }

   static AttributeMethods forAnnotationType(@Nullable Class<? extends Annotation> annotationType) {
      return annotationType == null ? NONE : cache.computeIfAbsent(annotationType, AttributeMethods::compute);
   }

   private static AttributeMethods compute(Class<? extends Annotation> annotationType) {
      Method[] methods = annotationType.getDeclaredMethods();
      int size = methods.length;

      for (int i = 0; i < methods.length; i++) {
         if (!isAttributeMethod(methods[i])) {
            methods[i] = null;
            size--;
         }
      }

      if (size == 0) {
         return NONE;
      }

      Arrays.sort(methods, methodComparator);
      Method[] attributeMethods = Arrays.copyOf(methods, size);
      return new AttributeMethods(annotationType, attributeMethods);
   }

   private static boolean isAttributeMethod(Method method) {
      return method.getParameterCount() == 0 && method.getReturnType() != void.class;
   }

   static String describe(@Nullable Method attribute) {
      return attribute == null ? "(none)" : describe(attribute.getDeclaringClass(), attribute.getName());
   }

   static String describe(@Nullable Class<?> annotationType, @Nullable String attributeName) {
      if (attributeName == null) {
         return "(none)";
      }

      String in = annotationType != null ? " in annotation [" + ClassUtils.getCanonicalName(annotationType) + "]" : "";
      return "attribute '" + attributeName + "'" + in;
   }
}
