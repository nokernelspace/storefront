package org.springframework.core.convert;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.core.MethodParameter;
import org.springframework.util.ConcurrentReferenceHashMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

public final class Property {
   private static final Map<Property, Annotation[]> annotationCache = new ConcurrentReferenceHashMap<>();
   private final Class<?> objectType;
   private final @Nullable Method readMethod;
   private final @Nullable Method writeMethod;
   private final String name;
   private final MethodParameter methodParameter;
   private Annotation @Nullable [] annotations;

   public Property(Class<?> objectType, @Nullable Method readMethod, @Nullable Method writeMethod) {
      this(objectType, readMethod, writeMethod, null);
   }

   public Property(Class<?> objectType, @Nullable Method readMethod, @Nullable Method writeMethod, @Nullable String name) {
      this.objectType = objectType;
      this.readMethod = readMethod;
      this.writeMethod = writeMethod;
      this.methodParameter = this.resolveMethodParameter();
      this.name = name != null ? name : this.resolveName();
   }

   public Class<?> getObjectType() {
      return this.objectType;
   }

   public String getName() {
      return this.name;
   }

   public Class<?> getType() {
      return this.methodParameter.getParameterType();
   }

   public @Nullable Method getReadMethod() {
      return this.readMethod;
   }

   public @Nullable Method getWriteMethod() {
      return this.writeMethod;
   }

   MethodParameter getMethodParameter() {
      return this.methodParameter;
   }

   Annotation[] getAnnotations() {
      if (this.annotations == null) {
         this.annotations = this.resolveAnnotations();
      }

      return this.annotations;
   }

   private String resolveName() {
      if (this.readMethod != null) {
         String methodName = this.readMethod.getName();
         int index;
         if (methodName.startsWith("get")) {
            index = isPlainAccessor(this.readMethod) ? 0 : 3;
         } else if (methodName.startsWith("is")) {
            index = isPlainAccessor(this.readMethod) ? 0 : 2;
         } else {
            index = 0;
         }

         return StringUtils.uncapitalize(methodName.substring(index));
      } else if (this.writeMethod != null) {
         String methodName = this.writeMethod.getName();
         if (!methodName.startsWith("set")) {
            throw new IllegalArgumentException("Not a setter method");
         } else {
            return StringUtils.uncapitalize(methodName.substring(3));
         }
      } else {
         throw new IllegalStateException("Property is neither readable nor writable");
      }
   }

   private static boolean isPlainAccessor(Method method) {
      if (!Modifier.isStatic(method.getModifiers()) && method.getParameterCount() <= 0 && method.getReturnType() != void.class) {
         try {
            Field field = method.getDeclaringClass().getDeclaredField(method.getName());
            return !Modifier.isStatic(field.getModifiers());
         } catch (Exception ex) {
            return false;
         }
      } else {
         return false;
      }
   }

   private MethodParameter resolveMethodParameter() {
      MethodParameter read = this.resolveReadMethodParameter();
      MethodParameter write = this.resolveWriteMethodParameter();
      if (write == null) {
         if (read == null) {
            throw new IllegalStateException("Property is neither readable nor writable");
         } else {
            return read;
         }
      } else {
         if (read != null) {
            Class<?> readType = read.getParameterType();
            Class<?> writeType = write.getParameterType();
            if (!writeType.equals(readType) && writeType.isAssignableFrom(readType)) {
               return read;
            }
         }

         return write;
      }
   }

   private @Nullable MethodParameter resolveReadMethodParameter() {
      return this.getReadMethod() == null ? null : new MethodParameter(this.getReadMethod(), -1).withContainingClass(this.getObjectType());
   }

   private @Nullable MethodParameter resolveWriteMethodParameter() {
      return this.getWriteMethod() == null ? null : new MethodParameter(this.getWriteMethod(), 0).withContainingClass(this.getObjectType());
   }

   private Annotation[] resolveAnnotations() {
      Annotation[] annotations = annotationCache.get(this);
      if (annotations == null) {
         Map<Class<? extends Annotation>, Annotation> annotationMap = new LinkedHashMap<>();
         this.addAnnotationsToMap(annotationMap, this.getReadMethod());
         this.addAnnotationsToMap(annotationMap, this.getWriteMethod());
         this.addAnnotationsToMap(annotationMap, this.getField());
         annotations = annotationMap.values().toArray(new Annotation[0]);
         annotationCache.put(this, annotations);
      }

      return annotations;
   }

   private void addAnnotationsToMap(Map<Class<? extends Annotation>, Annotation> annotationMap, @Nullable AnnotatedElement object) {
      if (object != null) {
         for (Annotation annotation : object.getAnnotations()) {
            annotationMap.put(annotation.annotationType(), annotation);
         }
      }
   }

   private @Nullable Field getField() {
      String name = this.getName();
      if (!StringUtils.hasLength(name)) {
         return null;
      }

      Field field = null;
      Class<?> declaringClass = this.declaringClass();
      if (declaringClass != null) {
         field = ReflectionUtils.findField(declaringClass, name);
         if (field == null) {
            field = ReflectionUtils.findField(declaringClass, StringUtils.uncapitalize(name));
            if (field == null) {
               field = ReflectionUtils.findField(declaringClass, StringUtils.capitalize(name));
            }
         }
      }

      return field;
   }

   private @Nullable Class<?> declaringClass() {
      if (this.getReadMethod() != null) {
         return this.getReadMethod().getDeclaringClass();
      } else {
         return this.getWriteMethod() != null ? this.getWriteMethod().getDeclaringClass() : null;
      }
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof Property that
            && ObjectUtils.nullSafeEquals(this.objectType, that.objectType)
            && ObjectUtils.nullSafeEquals(this.name, that.name)
            && ObjectUtils.nullSafeEquals(this.readMethod, that.readMethod)
            && ObjectUtils.nullSafeEquals(this.writeMethod, that.writeMethod);
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.objectType, this.name);
   }
}
