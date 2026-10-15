package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

public class AnnotationAttributes extends LinkedHashMap<String, Object> {
   private static final String UNKNOWN = "unknown";
   private final @Nullable Class<? extends Annotation> annotationType;
   final String displayName;
   final boolean validated;

   public AnnotationAttributes() {
      this.annotationType = null;
      this.displayName = "unknown";
      this.validated = false;
   }

   public AnnotationAttributes(int initialCapacity) {
      super(initialCapacity);
      this.annotationType = null;
      this.displayName = "unknown";
      this.validated = false;
   }

   public AnnotationAttributes(Map<String, @Nullable Object> map) {
      super(map);
      this.annotationType = null;
      this.displayName = "unknown";
      this.validated = false;
   }

   public AnnotationAttributes(AnnotationAttributes other) {
      super(other);
      this.annotationType = other.annotationType;
      this.displayName = other.displayName;
      this.validated = other.validated;
   }

   public AnnotationAttributes(Class<? extends Annotation> annotationType) {
      this(annotationType, false);
   }

   AnnotationAttributes(Class<? extends Annotation> annotationType, boolean validated) {
      Assert.notNull(annotationType, "'annotationType' must not be null");
      this.annotationType = annotationType;
      this.displayName = ClassUtils.getCanonicalName(annotationType);
      this.validated = validated;
   }

   public AnnotationAttributes(String annotationType, @Nullable ClassLoader classLoader) {
      Assert.notNull(annotationType, "'annotationType' must not be null");
      this.annotationType = getAnnotationType(annotationType, classLoader);
      this.displayName = annotationType;
      this.validated = false;
   }

   private static @Nullable Class<? extends Annotation> getAnnotationType(String annotationType, @Nullable ClassLoader classLoader) {
      if (classLoader != null) {
         try {
            return (Class<? extends Annotation>)classLoader.loadClass(annotationType);
         } catch (ClassNotFoundException var3) {
         }
      }

      return null;
   }

   public @Nullable Class<? extends Annotation> annotationType() {
      return this.annotationType;
   }

   public String getString(String attributeName) {
      return this.getRequiredAttribute(attributeName, String.class);
   }

   public String[] getStringArray(String attributeName) {
      return this.getRequiredAttribute(attributeName, String[].class);
   }

   public boolean getBoolean(String attributeName) {
      return this.getRequiredAttribute(attributeName, Boolean.class);
   }

   public <N extends Number> N getNumber(String attributeName) {
      return this.getRequiredAttribute(attributeName, (Class<N>)Number.class);
   }

   public <E extends Enum<?>> E getEnum(String attributeName) {
      return this.getRequiredAttribute(attributeName, (Class<E>)Enum.class);
   }

   public <T> Class<? extends T> getClass(String attributeName) {
      return this.getRequiredAttribute(attributeName, Class.class);
   }

   public Class<?>[] getClassArray(String attributeName) {
      return this.getRequiredAttribute(attributeName, Class[].class);
   }

   public AnnotationAttributes getAnnotation(String attributeName) {
      return this.getRequiredAttribute(attributeName, AnnotationAttributes.class);
   }

   public <A extends Annotation> A getAnnotation(String attributeName, Class<A> annotationType) {
      return this.getRequiredAttribute(attributeName, annotationType);
   }

   public AnnotationAttributes[] getAnnotationArray(String attributeName) {
      return this.getRequiredAttribute(attributeName, AnnotationAttributes[].class);
   }

   public <A extends Annotation> A[] getAnnotationArray(String attributeName, Class<A> annotationType) {
      return (A[])((Annotation[])this.getRequiredAttribute(attributeName, annotationType.arrayType()));
   }

   private <T> T getRequiredAttribute(String attributeName, Class<T> expectedType) {
      Assert.hasText(attributeName, "'attributeName' must not be null or empty");
      Object value = this.get(attributeName);
      if (value == null) {
         throw new IllegalArgumentException(String.format("Attribute '%s' not found in attributes for annotation [%s]", attributeName, this.displayName));
      } else if (value instanceof Throwable throwable) {
         throw new IllegalArgumentException(
            String.format("Attribute '%s' for annotation [%s] was not resolvable due to exception [%s]", attributeName, this.displayName, value), throwable
         );
      } else {
         if (!expectedType.isInstance(value) && expectedType.isArray() && expectedType.componentType().isInstance(value)) {
            Object array = Array.newInstance(expectedType.componentType(), 1);
            Array.set(array, 0, value);
            value = array;
         }

         if (!expectedType.isInstance(value)) {
            throw new IllegalArgumentException(
               String.format(
                  "Attribute '%s' is of type %s, but %s was expected in attributes for annotation [%s]",
                  attributeName,
                  value.getClass().getSimpleName(),
                  expectedType.getSimpleName(),
                  this.displayName
               )
            );
         } else {
            return (T)value;
         }
      }
   }

   @Override
   public String toString() {
      Iterator<Entry<String, Object>> entries = this.entrySet().iterator();
      StringBuilder sb = new StringBuilder("{");

      while (entries.hasNext()) {
         Entry<String, Object> entry = entries.next();
         sb.append(entry.getKey());
         sb.append('=');
         sb.append(this.valueToString(entry.getValue()));
         if (entries.hasNext()) {
            sb.append(", ");
         }
      }

      sb.append('}');
      return sb.toString();
   }

   private String valueToString(@Nullable Object value) {
      if (value == this) {
         return "(this Map)";
      } else {
         return value instanceof Object[] objects ? "[" + StringUtils.arrayToDelimitedString(objects, ", ") + "]" : String.valueOf(value);
      }
   }

   public static @Nullable AnnotationAttributes fromMap(@Nullable Map<String, @Nullable Object> map) {
      if (map == null) {
         return null;
      } else {
         return map instanceof AnnotationAttributes annotationAttributes ? annotationAttributes : new AnnotationAttributes(map);
      }
   }
}
