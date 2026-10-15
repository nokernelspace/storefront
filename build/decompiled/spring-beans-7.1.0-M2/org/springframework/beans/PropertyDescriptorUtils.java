package org.springframework.beans;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

abstract class PropertyDescriptorUtils {
   public static final PropertyDescriptor[] EMPTY_PROPERTY_DESCRIPTOR_ARRAY = new PropertyDescriptor[0];

   public static Collection<? extends PropertyDescriptor> determineBasicProperties(Class<?> beanClass) throws IntrospectionException {
      Map<String, PropertyDescriptorUtils.BasicPropertyDescriptor> pdMap = new TreeMap<>();

      for (Method method : beanClass.getMethods()) {
         String methodName = method.getName();
         boolean setter;
         int nameIndex;
         if (methodName.startsWith("set") && method.getParameterCount() == 1) {
            setter = true;
            nameIndex = 3;
         } else if (methodName.startsWith("get")
            && method.getParameterCount() == 0
            && method.getReturnType() != void.class
            && !Modifier.isStatic(method.getModifiers())) {
            setter = false;
            nameIndex = 3;
         } else {
            if (!methodName.startsWith("is")
               || method.getParameterCount() != 0
               || method.getReturnType() != boolean.class
               || Modifier.isStatic(method.getModifiers())) {
               continue;
            }

            setter = false;
            nameIndex = 2;
         }

         String propertyName = StringUtils.uncapitalizeAsProperty(methodName.substring(nameIndex));
         if (!propertyName.isEmpty()) {
            PropertyDescriptorUtils.BasicPropertyDescriptor pd = pdMap.get(propertyName);
            if (pd != null) {
               if (setter) {
                  pd.addWriteMethod(method);
               } else {
                  Method readMethod = pd.getReadMethod();
                  if (readMethod == null || readMethod.getReturnType().isAssignableFrom(method.getReturnType())) {
                     pd.setReadMethod(method);
                  }
               }
            } else {
               pd = new PropertyDescriptorUtils.BasicPropertyDescriptor(propertyName, beanClass, !setter ? method : null, setter ? method : null);
               pdMap.put(propertyName, pd);
            }
         }
      }

      return pdMap.values();
   }

   public static void copyNonMethodProperties(PropertyDescriptor source, PropertyDescriptor target) {
      target.setExpert(source.isExpert());
      target.setHidden(source.isHidden());
      target.setPreferred(source.isPreferred());
      target.setName(source.getName());
      target.setShortDescription(source.getShortDescription());
      target.setDisplayName(source.getDisplayName());
      Enumeration<String> keys = source.attributeNames();

      while (keys.hasMoreElements()) {
         String key = keys.nextElement();
         target.setValue(key, source.getValue(key));
      }

      target.setPropertyEditorClass(source.getPropertyEditorClass());
      target.setBound(source.isBound());
      target.setConstrained(source.isConstrained());
   }

   public static @Nullable Class<?> findPropertyType(@Nullable Method readMethod, @Nullable Method writeMethod) throws IntrospectionException {
      Class<?> propertyType = null;
      if (readMethod != null) {
         if (readMethod.getParameterCount() != 0) {
            throw new IntrospectionException("Bad read method arg count: " + readMethod);
         }

         propertyType = readMethod.getReturnType();
         if (propertyType == void.class) {
            throw new IntrospectionException("Read method returns void: " + readMethod);
         }
      }

      if (writeMethod != null) {
         Class<?>[] params = writeMethod.getParameterTypes();
         if (params.length != 1) {
            throw new IntrospectionException("Bad write method arg count: " + writeMethod);
         }

         if (propertyType != null) {
            if (propertyType.isAssignableFrom(params[0])) {
               propertyType = params[0];
            } else if (!params[0].isAssignableFrom(propertyType)) {
               throw new IntrospectionException("Type mismatch between read and write methods: " + readMethod + " - " + writeMethod);
            }
         } else {
            propertyType = params[0];
         }
      }

      return propertyType;
   }

   public static @Nullable Class<?> findIndexedPropertyType(
      String name, @Nullable Class<?> propertyType, @Nullable Method indexedReadMethod, @Nullable Method indexedWriteMethod
   ) throws IntrospectionException {
      Class<?> indexedPropertyType = null;
      if (indexedReadMethod != null) {
         Class<?>[] params = indexedReadMethod.getParameterTypes();
         if (params.length != 1) {
            throw new IntrospectionException("Bad indexed read method arg count: " + indexedReadMethod);
         }

         if (params[0] != int.class) {
            throw new IntrospectionException("Non int index to indexed read method: " + indexedReadMethod);
         }

         indexedPropertyType = indexedReadMethod.getReturnType();
         if (indexedPropertyType == void.class) {
            throw new IntrospectionException("Indexed read method returns void: " + indexedReadMethod);
         }
      }

      if (indexedWriteMethod != null) {
         Class<?>[] params = indexedWriteMethod.getParameterTypes();
         if (params.length != 2) {
            throw new IntrospectionException("Bad indexed write method arg count: " + indexedWriteMethod);
         }

         if (params[0] != int.class) {
            throw new IntrospectionException("Non int index to indexed write method: " + indexedWriteMethod);
         }

         if (indexedPropertyType != null) {
            if (indexedPropertyType.isAssignableFrom(params[1])) {
               indexedPropertyType = params[1];
            } else if (!params[1].isAssignableFrom(indexedPropertyType)) {
               throw new IntrospectionException("Type mismatch between indexed read and write methods: " + indexedReadMethod + " - " + indexedWriteMethod);
            }
         } else {
            indexedPropertyType = params[1];
         }
      }

      if (propertyType == null || propertyType.isArray() && propertyType.componentType() == indexedPropertyType) {
         return indexedPropertyType;
      } else {
         throw new IntrospectionException("Type mismatch between indexed and non-indexed methods: " + indexedReadMethod + " - " + indexedWriteMethod);
      }
   }

   public static boolean equals(PropertyDescriptor pd, PropertyDescriptor otherPd) {
      return ObjectUtils.nullSafeEquals(pd.getReadMethod(), otherPd.getReadMethod())
         && ObjectUtils.nullSafeEquals(pd.getWriteMethod(), otherPd.getWriteMethod())
         && ObjectUtils.nullSafeEquals(pd.getPropertyType(), otherPd.getPropertyType())
         && ObjectUtils.nullSafeEquals(pd.getPropertyEditorClass(), otherPd.getPropertyEditorClass())
         && pd.isBound() == otherPd.isBound()
         && pd.isConstrained() == otherPd.isConstrained();
   }

   private static class BasicPropertyDescriptor extends PropertyDescriptor {
      private final Class<?> beanClass;
      private @Nullable Method readMethod;
      private @Nullable Method writeMethod;
      private final List<Method> candidateWriteMethods = new ArrayList<>();

      public BasicPropertyDescriptor(String propertyName, Class<?> beanClass, @Nullable Method readMethod, @Nullable Method writeMethod) throws IntrospectionException {
         super(propertyName, readMethod, writeMethod);
         this.beanClass = beanClass;
      }

      @Override
      public void setReadMethod(@Nullable Method readMethod) {
         this.readMethod = readMethod;
      }

      @Override
      public @Nullable Method getReadMethod() {
         return this.readMethod;
      }

      @Override
      public void setWriteMethod(@Nullable Method writeMethod) {
         this.writeMethod = writeMethod;
      }

      void addWriteMethod(Method writeMethod) {
         if (this.writeMethod != null) {
            this.candidateWriteMethods.add(this.writeMethod);
            this.writeMethod = null;
         }

         this.candidateWriteMethods.add(writeMethod);
      }

      @Override
      public @Nullable Method getWriteMethod() {
         if (this.writeMethod == null && !this.candidateWriteMethods.isEmpty()) {
            if (this.readMethod != null && this.candidateWriteMethods.size() != 1) {
               Class<?> resolvedReadType = ResolvableType.forMethodReturnType(this.readMethod, this.beanClass).toClass();

               for (Method method : this.candidateWriteMethods) {
                  Class<?> resolvedWriteType = ResolvableType.forMethodParameter(method, 0, this.beanClass).toClass();
                  if (resolvedReadType.equals(resolvedWriteType)) {
                     this.writeMethod = method;
                     break;
                  }

                  Class<?> parameterType = method.getParameterTypes()[0];
                  if (this.readMethod.getReturnType().isAssignableFrom(parameterType)
                     && (this.writeMethod == null || this.writeMethod.getParameterTypes()[0].isAssignableFrom(parameterType))) {
                     this.writeMethod = method;
                  }
               }
            } else {
               this.writeMethod = this.candidateWriteMethods.get(0);
            }
         }

         return this.writeMethod;
      }
   }
}
