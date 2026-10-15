package org.springframework.beans;

import java.awt.Image;
import java.beans.BeanDescriptor;
import java.beans.BeanInfo;
import java.beans.EventSetDescriptor;
import java.beans.IndexedPropertyDescriptor;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.MethodDescriptor;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ObjectUtils;

class ExtendedBeanInfo implements BeanInfo {
   private static final Log logger = LogFactory.getLog(ExtendedBeanInfo.class);
   private final BeanInfo delegate;
   private final Set<PropertyDescriptor> propertyDescriptors = new TreeSet<>(new ExtendedBeanInfo.PropertyDescriptorComparator());

   public ExtendedBeanInfo(BeanInfo delegate) {
      this.delegate = delegate;

      for (PropertyDescriptor pd : delegate.getPropertyDescriptors()) {
         try {
            this.propertyDescriptors
               .add(
                  pd instanceof IndexedPropertyDescriptor indexedPd
                     ? new ExtendedBeanInfo.SimpleIndexedPropertyDescriptor(indexedPd)
                     : new ExtendedBeanInfo.SimplePropertyDescriptor(pd)
               );
         } catch (IntrospectionException ex) {
            if (logger.isDebugEnabled()) {
               logger.debug("Ignoring invalid bean property '" + pd.getName() + "': " + ex.getMessage());
            }
         }
      }

      MethodDescriptor[] methodDescriptors = delegate.getMethodDescriptors();
      if (methodDescriptors != null) {
         for (Method method : this.findCandidateWriteMethods(methodDescriptors)) {
            try {
               this.handleCandidateWriteMethod(method);
            } catch (IntrospectionException ex) {
               if (logger.isDebugEnabled()) {
                  logger.debug("Ignoring candidate write method [" + method + "]: " + ex.getMessage());
               }
            }
         }
      }
   }

   private List<Method> findCandidateWriteMethods(MethodDescriptor[] methodDescriptors) {
      List<Method> matches = new ArrayList<>();

      for (MethodDescriptor methodDescriptor : methodDescriptors) {
         Method method = methodDescriptor.getMethod();
         if (isCandidateWriteMethod(method)) {
            matches.add(method);
         }
      }

      matches.sort(Comparator.comparing(Method::toString).reversed());
      return matches;
   }

   public static boolean isCandidateWriteMethod(Method method) {
      String methodName = method.getName();
      int nParams = method.getParameterCount();
      return methodName.length() > 3
         && methodName.startsWith("set")
         && Modifier.isPublic(method.getModifiers())
         && (!void.class.isAssignableFrom(method.getReturnType()) || Modifier.isStatic(method.getModifiers()))
         && (nParams == 1 || nParams == 2 && int.class == method.getParameterTypes()[0]);
   }

   private void handleCandidateWriteMethod(Method method) throws IntrospectionException {
      int nParams = method.getParameterCount();
      String propertyName = this.propertyNameFor(method);
      Class<?> propertyType = method.getParameterTypes()[nParams - 1];
      PropertyDescriptor existingPd = this.findExistingPropertyDescriptor(propertyName, propertyType);
      if (nParams == 1) {
         if (existingPd == null) {
            this.propertyDescriptors.add(new ExtendedBeanInfo.SimplePropertyDescriptor(propertyName, null, method));
         } else {
            existingPd.setWriteMethod(method);
         }
      } else {
         if (nParams != 2) {
            throw new IllegalArgumentException("Write method must have exactly 1 or 2 parameters: " + method);
         }

         if (existingPd == null) {
            this.propertyDescriptors.add(new ExtendedBeanInfo.SimpleIndexedPropertyDescriptor(propertyName, null, null, null, method));
         } else if (existingPd instanceof IndexedPropertyDescriptor indexedPd) {
            indexedPd.setIndexedWriteMethod(method);
         } else {
            this.propertyDescriptors.remove(existingPd);
            this.propertyDescriptors
               .add(new ExtendedBeanInfo.SimpleIndexedPropertyDescriptor(propertyName, existingPd.getReadMethod(), existingPd.getWriteMethod(), null, method));
         }
      }
   }

   private @Nullable PropertyDescriptor findExistingPropertyDescriptor(String propertyName, Class<?> propertyType) {
      for (PropertyDescriptor pd : this.propertyDescriptors) {
         String candidateName = pd.getName();
         if (pd instanceof IndexedPropertyDescriptor indexedPd) {
            Class<?> candidateType = indexedPd.getIndexedPropertyType();
            if (candidateName.equals(propertyName) && (candidateType.equals(propertyType) || candidateType.equals(propertyType.componentType()))) {
               return pd;
            }
         } else {
            Class<?> candidateType = pd.getPropertyType();
            if (candidateName.equals(propertyName) && (candidateType.equals(propertyType) || propertyType.equals(candidateType.componentType()))) {
               return pd;
            }
         }
      }

      return null;
   }

   private String propertyNameFor(Method method) {
      return Introspector.decapitalize(method.getName().substring(3));
   }

   @Override
   public PropertyDescriptor[] getPropertyDescriptors() {
      return this.propertyDescriptors.toArray(PropertyDescriptorUtils.EMPTY_PROPERTY_DESCRIPTOR_ARRAY);
   }

   @Override
   public BeanInfo[] getAdditionalBeanInfo() {
      return this.delegate.getAdditionalBeanInfo();
   }

   @Override
   public BeanDescriptor getBeanDescriptor() {
      return this.delegate.getBeanDescriptor();
   }

   @Override
   public int getDefaultEventIndex() {
      return this.delegate.getDefaultEventIndex();
   }

   @Override
   public int getDefaultPropertyIndex() {
      return this.delegate.getDefaultPropertyIndex();
   }

   @Override
   public EventSetDescriptor[] getEventSetDescriptors() {
      return this.delegate.getEventSetDescriptors();
   }

   @Override
   public Image getIcon(int iconKind) {
      return this.delegate.getIcon(iconKind);
   }

   @Override
   public MethodDescriptor[] getMethodDescriptors() {
      return this.delegate.getMethodDescriptors();
   }

   static class PropertyDescriptorComparator implements Comparator<PropertyDescriptor> {
      public int compare(PropertyDescriptor desc1, PropertyDescriptor desc2) {
         return desc1.getName().compareTo(desc2.getName());
      }
   }

   static class SimpleIndexedPropertyDescriptor extends IndexedPropertyDescriptor {
      private @Nullable Method readMethod;
      private @Nullable Method writeMethod;
      private @Nullable Class<?> propertyType;
      private @Nullable Method indexedReadMethod;
      private @Nullable Method indexedWriteMethod;
      private @Nullable Class<?> indexedPropertyType;
      private @Nullable Class<?> propertyEditorClass;

      public SimpleIndexedPropertyDescriptor(IndexedPropertyDescriptor original) throws IntrospectionException {
         this(original.getName(), original.getReadMethod(), original.getWriteMethod(), original.getIndexedReadMethod(), original.getIndexedWriteMethod());
         PropertyDescriptorUtils.copyNonMethodProperties(original, this);
      }

      public SimpleIndexedPropertyDescriptor(
         String propertyName, @Nullable Method readMethod, @Nullable Method writeMethod, @Nullable Method indexedReadMethod, Method indexedWriteMethod
      ) throws IntrospectionException {
         super(propertyName, null, null, null, null);
         this.readMethod = readMethod;
         this.writeMethod = writeMethod;
         this.propertyType = PropertyDescriptorUtils.findPropertyType(readMethod, writeMethod);
         this.indexedReadMethod = indexedReadMethod;
         this.indexedWriteMethod = indexedWriteMethod;
         this.indexedPropertyType = PropertyDescriptorUtils.findIndexedPropertyType(propertyName, this.propertyType, indexedReadMethod, indexedWriteMethod);
      }

      @Override
      public @Nullable Method getReadMethod() {
         return this.readMethod;
      }

      @Override
      public void setReadMethod(@Nullable Method readMethod) {
         this.readMethod = readMethod;
      }

      @Override
      public @Nullable Method getWriteMethod() {
         return this.writeMethod;
      }

      @Override
      public void setWriteMethod(@Nullable Method writeMethod) {
         this.writeMethod = writeMethod;
      }

      @Override
      public @Nullable Class<?> getPropertyType() {
         if (this.propertyType == null) {
            try {
               this.propertyType = PropertyDescriptorUtils.findPropertyType(this.readMethod, this.writeMethod);
            } catch (IntrospectionException var2) {
            }
         }

         return this.propertyType;
      }

      @Override
      public @Nullable Method getIndexedReadMethod() {
         return this.indexedReadMethod;
      }

      @Override
      public void setIndexedReadMethod(@Nullable Method indexedReadMethod) throws IntrospectionException {
         this.indexedReadMethod = indexedReadMethod;
      }

      @Override
      public @Nullable Method getIndexedWriteMethod() {
         return this.indexedWriteMethod;
      }

      @Override
      public void setIndexedWriteMethod(@Nullable Method indexedWriteMethod) throws IntrospectionException {
         this.indexedWriteMethod = indexedWriteMethod;
      }

      @Override
      public @Nullable Class<?> getIndexedPropertyType() {
         if (this.indexedPropertyType == null) {
            try {
               this.indexedPropertyType = PropertyDescriptorUtils.findIndexedPropertyType(
                  this.getName(), this.getPropertyType(), this.indexedReadMethod, this.indexedWriteMethod
               );
            } catch (IntrospectionException var2) {
            }
         }

         return this.indexedPropertyType;
      }

      @Override
      public @Nullable Class<?> getPropertyEditorClass() {
         return this.propertyEditorClass;
      }

      @Override
      public void setPropertyEditorClass(@Nullable Class<?> propertyEditorClass) {
         this.propertyEditorClass = propertyEditorClass;
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other
            || other instanceof IndexedPropertyDescriptor that
               && ObjectUtils.nullSafeEquals(this.getIndexedReadMethod(), that.getIndexedReadMethod())
               && ObjectUtils.nullSafeEquals(this.getIndexedWriteMethod(), that.getIndexedWriteMethod())
               && ObjectUtils.nullSafeEquals(this.getIndexedPropertyType(), that.getIndexedPropertyType())
               && PropertyDescriptorUtils.equals(this, that);
      }

      @Override
      public int hashCode() {
         return Objects.hash(this.getReadMethod(), this.getWriteMethod(), this.getIndexedReadMethod(), this.getIndexedWriteMethod());
      }

      @Override
      public String toString() {
         return String.format(
            "%s[name=%s, propertyType=%s, indexedPropertyType=%s, readMethod=%s, writeMethod=%s, indexedReadMethod=%s, indexedWriteMethod=%s]",
            this.getClass().getSimpleName(),
            this.getName(),
            this.getPropertyType(),
            this.getIndexedPropertyType(),
            this.readMethod,
            this.writeMethod,
            this.indexedReadMethod,
            this.indexedWriteMethod
         );
      }
   }

   static class SimplePropertyDescriptor extends PropertyDescriptor {
      private @Nullable Method readMethod;
      private @Nullable Method writeMethod;
      private @Nullable Class<?> propertyType;
      private @Nullable Class<?> propertyEditorClass;

      public SimplePropertyDescriptor(PropertyDescriptor original) throws IntrospectionException {
         this(original.getName(), original.getReadMethod(), original.getWriteMethod());
         PropertyDescriptorUtils.copyNonMethodProperties(original, this);
      }

      public SimplePropertyDescriptor(String propertyName, @Nullable Method readMethod, Method writeMethod) throws IntrospectionException {
         super(propertyName, null, null);
         this.readMethod = readMethod;
         this.writeMethod = writeMethod;
         this.propertyType = PropertyDescriptorUtils.findPropertyType(readMethod, writeMethod);
      }

      @Override
      public @Nullable Method getReadMethod() {
         return this.readMethod;
      }

      @Override
      public void setReadMethod(@Nullable Method readMethod) {
         this.readMethod = readMethod;
      }

      @Override
      public @Nullable Method getWriteMethod() {
         return this.writeMethod;
      }

      @Override
      public void setWriteMethod(@Nullable Method writeMethod) {
         this.writeMethod = writeMethod;
      }

      @Override
      public @Nullable Class<?> getPropertyType() {
         if (this.propertyType == null) {
            try {
               this.propertyType = PropertyDescriptorUtils.findPropertyType(this.readMethod, this.writeMethod);
            } catch (IntrospectionException var2) {
            }
         }

         return this.propertyType;
      }

      @Override
      public @Nullable Class<?> getPropertyEditorClass() {
         return this.propertyEditorClass;
      }

      @Override
      public void setPropertyEditorClass(@Nullable Class<?> propertyEditorClass) {
         this.propertyEditorClass = propertyEditorClass;
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other || other instanceof PropertyDescriptor that && PropertyDescriptorUtils.equals(this, that);
      }

      @Override
      public int hashCode() {
         return Objects.hash(this.getReadMethod(), this.getWriteMethod());
      }

      @Override
      public String toString() {
         return String.format(
            "%s[name=%s, propertyType=%s, readMethod=%s, writeMethod=%s]",
            this.getClass().getSimpleName(),
            this.getName(),
            this.getPropertyType(),
            this.readMethod,
            this.writeMethod
         );
      }
   }
}
