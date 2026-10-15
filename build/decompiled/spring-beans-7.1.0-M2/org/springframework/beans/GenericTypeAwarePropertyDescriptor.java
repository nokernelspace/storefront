package org.springframework.beans;

import java.beans.IntrospectionException;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.core.MethodParameter;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.Property;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

final class GenericTypeAwarePropertyDescriptor extends PropertyDescriptor {
   private final Class<?> beanClass;
   private final @Nullable Method readMethod;
   private final @Nullable Method writeMethod;
   private @Nullable Set<Method> ambiguousWriteMethods;
   private volatile boolean ambiguousWriteMethodsLogged;
   private @Nullable MethodParameter writeMethodParameter;
   private volatile @Nullable ResolvableType writeMethodType;
   private @Nullable ResolvableType readMethodType;
   private volatile @Nullable TypeDescriptor typeDescriptor;
   private @Nullable Class<?> propertyType;
   private final @Nullable Class<?> propertyEditorClass;

   public GenericTypeAwarePropertyDescriptor(
      Class<?> beanClass, String propertyName, @Nullable Method readMethod, @Nullable Method writeMethod, @Nullable Class<?> propertyEditorClass
   ) throws IntrospectionException {
      super(propertyName, null, null);
      this.beanClass = beanClass;
      Method readMethodToUse = readMethod != null ? BridgeMethodResolver.findBridgedMethod(readMethod) : null;
      Method writeMethodToUse = writeMethod != null ? BridgeMethodResolver.findBridgedMethod(writeMethod) : null;
      if (writeMethodToUse == null && readMethodToUse != null) {
         Method candidate = ClassUtils.getMethodIfAvailable(this.beanClass, "set" + StringUtils.capitalize(this.getName()), (Class[])null);
         if (candidate != null && candidate.getParameterCount() == 1) {
            writeMethodToUse = candidate;
         }
      }

      this.readMethod = readMethodToUse;
      this.writeMethod = writeMethodToUse;
      if (this.writeMethod != null) {
         if (this.readMethod == null) {
            Set<Method> ambiguousCandidates = new HashSet<>();

            for (Method method : beanClass.getMethods()) {
               if (method.getName().equals(this.writeMethod.getName())
                  && !method.equals(this.writeMethod)
                  && !method.isBridge()
                  && method.getParameterCount() == this.writeMethod.getParameterCount()) {
                  ambiguousCandidates.add(method);
               }
            }

            if (!ambiguousCandidates.isEmpty()) {
               this.ambiguousWriteMethods = ambiguousCandidates;
            }
         }

         this.writeMethodParameter = new MethodParameter(this.writeMethod, 0).withContainingClass(this.beanClass);
      }

      if (this.readMethod != null) {
         this.readMethodType = ResolvableType.forMethodReturnType(this.readMethod, this.beanClass);
         this.propertyType = this.readMethodType.resolve(this.readMethod.getReturnType());
      } else if (this.writeMethodParameter != null) {
         this.propertyType = this.writeMethodParameter.getParameterType();
      }

      this.propertyEditorClass = propertyEditorClass;
   }

   public Class<?> getBeanClass() {
      return this.beanClass;
   }

   @Override
   public @Nullable Method getReadMethod() {
      return this.readMethod;
   }

   @Override
   public @Nullable Method getWriteMethod() {
      return this.writeMethod;
   }

   public Method getWriteMethodForActualAccess() {
      Assert.state(this.writeMethod != null, "No write method available");
      if (this.ambiguousWriteMethods != null && !this.ambiguousWriteMethodsLogged) {
         this.ambiguousWriteMethodsLogged = true;
         LogFactory.getLog(GenericTypeAwarePropertyDescriptor.class)
            .debug(
               "Non-unique JavaBean property '"
                  + this.getName()
                  + "' being accessed! Ambiguous write methods found next to actually used ["
                  + this.writeMethod
                  + "]: "
                  + this.ambiguousWriteMethods
            );
      }

      return this.writeMethod;
   }

   public @Nullable Method getWriteMethodFallback(@Nullable Class<?> valueType) {
      if (this.ambiguousWriteMethods != null) {
         for (Method method : this.ambiguousWriteMethods) {
            Class<?> paramType = method.getParameterTypes()[0];
            if (valueType != null ? paramType.isAssignableFrom(valueType) : !paramType.isPrimitive()) {
               return method;
            }
         }
      }

      return null;
   }

   public @Nullable Method getUniqueWriteMethodFallback() {
      return this.ambiguousWriteMethods != null && this.ambiguousWriteMethods.size() == 1 ? this.ambiguousWriteMethods.iterator().next() : null;
   }

   public boolean hasUniqueWriteMethod() {
      return this.writeMethod != null && this.ambiguousWriteMethods == null;
   }

   public MethodParameter getWriteMethodParameter() {
      Assert.state(this.writeMethodParameter != null, "No write method available");
      return this.writeMethodParameter;
   }

   public ResolvableType getWriteMethodType() {
      ResolvableType writeMethodType = this.writeMethodType;
      if (writeMethodType == null) {
         writeMethodType = ResolvableType.forMethodParameter(this.getWriteMethodParameter());
         this.writeMethodType = writeMethodType;
      }

      return writeMethodType;
   }

   public ResolvableType getReadMethodType() {
      Assert.state(this.readMethodType != null, "No read method available");
      return this.readMethodType;
   }

   public TypeDescriptor getTypeDescriptor() {
      TypeDescriptor typeDescriptor = this.typeDescriptor;
      if (typeDescriptor == null) {
         Property property = new Property(this.getBeanClass(), this.getReadMethod(), this.getWriteMethod(), this.getName());
         typeDescriptor = new TypeDescriptor(property);
         this.typeDescriptor = typeDescriptor;
      }

      return typeDescriptor;
   }

   @Override
   public @Nullable Class<?> getPropertyType() {
      return this.propertyType;
   }

   @Override
   public @Nullable Class<?> getPropertyEditorClass() {
      return this.propertyEditorClass;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof GenericTypeAwarePropertyDescriptor that
            && this.getBeanClass().equals(that.getBeanClass())
            && PropertyDescriptorUtils.equals(this, that);
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.getBeanClass(), this.getReadMethod(), this.getWriteMethod());
   }
}
