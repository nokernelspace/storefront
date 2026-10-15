package org.springframework.core.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

final class SynthesizedMergedAnnotationInvocationHandler<A extends Annotation> implements InvocationHandler {
   private final MergedAnnotation<?> annotation;
   private final Class<A> type;
   private final AttributeMethods attributes;
   private final Map<String, Object> valueCache = new ConcurrentHashMap<>(8);
   private volatile @Nullable Integer hashCode;
   private volatile @Nullable String string;

   private SynthesizedMergedAnnotationInvocationHandler(MergedAnnotation<A> annotation, Class<A> type) {
      Assert.notNull(annotation, "MergedAnnotation must not be null");
      Assert.notNull(type, "Type must not be null");
      Assert.isTrue(type.isAnnotation(), "Type must be an annotation");
      this.annotation = annotation;
      this.type = type;
      this.attributes = AttributeMethods.forAnnotationType(type);
   }

   @Override
   public Object invoke(Object proxy, Method method, Object[] args) {
      if (this.attributes.indexOf(method.getName()) != -1) {
         return this.getAttributeValue(method);
      }

      if (method.getParameterCount() == 0) {
         switch (method.getName()) {
            case "annotationType":
               return this.type;
            case "hashCode":
               return this.annotationHashCode();
            case "toString":
               return this.annotationToString();
         }
      }

      if (ReflectionUtils.isEqualsMethod(method)) {
         return this.annotationEquals(args[0]);
      } else {
         throw new AnnotationConfigurationException(String.format("Method [%s] is unsupported for synthesized annotation type [%s]", method, this.type));
      }
   }

   private boolean annotationEquals(Object other) {
      if (this == other) {
         return true;
      }

      if (!this.type.isInstance(other)) {
         return false;
      }

      for (int i = 0; i < this.attributes.size(); i++) {
         Method attribute = this.attributes.get(i);
         Object thisValue = this.getAttributeValue(attribute);
         Object otherValue = AnnotationUtils.invokeAnnotationMethod(attribute, other);
         if (!ObjectUtils.nullSafeEquals(thisValue, otherValue)) {
            return false;
         }
      }

      return true;
   }

   private int annotationHashCode() {
      Integer hashCode = this.hashCode;
      if (hashCode == null) {
         hashCode = this.computeHashCode();
         this.hashCode = hashCode;
      }

      return hashCode;
   }

   private Integer computeHashCode() {
      int hashCode = 0;

      for (int i = 0; i < this.attributes.size(); i++) {
         Method attribute = this.attributes.get(i);
         Object value = this.getAttributeValue(attribute);
         hashCode += 127 * attribute.getName().hashCode() ^ ObjectUtils.nullSafeHashCode(value);
      }

      return hashCode;
   }

   private String annotationToString() {
      String string = this.string;
      if (string == null) {
         StringBuilder builder = new StringBuilder("@").append(ClassUtils.getCanonicalName(this.type)).append('(');
         if (this.attributes.size() == 1 && this.attributes.get(0).getName().equals("value")) {
            builder.append(this.toString(this.getAttributeValue(this.attributes.get(0))));
         } else {
            for (int i = 0; i < this.attributes.size(); i++) {
               Method attribute = this.attributes.get(i);
               if (i > 0) {
                  builder.append(", ");
               }

               builder.append(attribute.getName());
               builder.append('=');
               builder.append(this.toString(this.getAttributeValue(attribute)));
            }
         }

         builder.append(')');
         string = builder.toString();
         this.string = string;
      }

      return string;
   }

   private String toString(Object value) {
      Class<?> type = value.getClass();
      if (type.isArray()) {
         StringBuilder builder = new StringBuilder("{");
         int arrayLength = Array.getLength(value);

         for (int i = 0; i < arrayLength; i++) {
            if (i > 0) {
               builder.append(", ");
            }

            builder.append(this.toString(Array.get(value, i)));
         }

         builder.append('}');
         return builder.toString();
      } else {
         if (type == String.class) {
            return "\"" + (String)value + "\"";
         }

         if (type == Character.class) {
            return "'" + value.toString() + "'";
         }

         if (type == Byte.class) {
            return String.format("(byte)0x%02x", value);
         }

         if (type == Long.class) {
            return Long.toString((Long)value) + "L";
         }

         if (type == Float.class) {
            float floatValue = (Float)value;
            if (Float.isNaN(floatValue)) {
               return "0.0f/0.0f";
            } else if (floatValue == Float.POSITIVE_INFINITY) {
               return "1.0f/0.0f";
            } else {
               return floatValue == Float.NEGATIVE_INFINITY ? "-1.0f/0.0f" : Float.toString(floatValue) + "f";
            }
         } else if (type == Double.class) {
            double doubleValue = (Double)value;
            if (Double.isNaN(doubleValue)) {
               return "0.0/0.0";
            } else if (doubleValue == Double.POSITIVE_INFINITY) {
               return "1.0/0.0";
            } else {
               return doubleValue == Double.NEGATIVE_INFINITY ? "-1.0/0.0" : Double.toString(doubleValue);
            }
         } else if (value instanceof Enum<?> e) {
            return e.name();
         } else {
            return type == Class.class ? ClassUtils.getCanonicalName((Class<?>)value) + ".class" : String.valueOf(value);
         }
      }
   }

   private Object getAttributeValue(Method method) {
      Object value = this.valueCache
         .computeIfAbsent(
            method.getName(),
            attributeName -> {
               Class<?> type = ClassUtils.resolvePrimitiveIfNecessary(method.getReturnType());
               return this.annotation
                  .getValue(attributeName, type)
                  .orElseThrow(
                     () -> new NoSuchElementException(
                        "No value found for attribute named '"
                           + attributeName
                           + "' in merged annotation "
                           + ClassUtils.getCanonicalName(this.annotation.getType())
                     )
                  );
            }
         );
      if (value.getClass().isArray() && Array.getLength(value) > 0) {
         value = this.cloneArray(value);
      }

      return value;
   }

   private Object cloneArray(Object array) {
      Class<?> type = array.getClass();
      if (type == boolean[].class) {
         return ((boolean[])array).clone();
      } else if (type == byte[].class) {
         return ((byte[])array).clone();
      } else if (type == char[].class) {
         return ((char[])array).clone();
      } else if (type == double[].class) {
         return ((double[])array).clone();
      } else if (type == float[].class) {
         return ((float[])array).clone();
      } else if (type == int[].class) {
         return ((int[])array).clone();
      } else if (type == long[].class) {
         return ((long[])array).clone();
      } else {
         return type == short[].class ? ((short[])array).clone() : ((Object[])array).clone();
      }
   }

   static <A extends Annotation> A createProxy(MergedAnnotation<A> annotation, Class<A> type) {
      ClassLoader classLoader = type.getClassLoader();
      Class<?>[] interfaces = new Class[]{type};
      InvocationHandler handler = new SynthesizedMergedAnnotationInvocationHandler<>(annotation, type);
      return (A)Proxy.newProxyInstance(classLoader, interfaces, handler);
   }
}
