package org.springframework.beans.factory.config;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.InjectionPoint;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.core.MethodParameter;
import org.springframework.core.Nullness;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.ResolvableType;
import org.springframework.core.convert.TypeDescriptor;
import org.springframework.util.ObjectUtils;

public class DependencyDescriptor extends InjectionPoint implements Serializable {
   private final Class<?> declaringClass;
   private @Nullable String methodName;
   private Class<?> @Nullable [] parameterTypes;
   private int parameterIndex;
   private @Nullable String fieldName;
   private final boolean required;
   private final boolean eager;
   private int nestingLevel = 1;
   private @Nullable Class<?> containingClass;
   private transient volatile @Nullable ResolvableType resolvableType;
   private transient volatile @Nullable TypeDescriptor typeDescriptor;

   public DependencyDescriptor(MethodParameter methodParameter, boolean required) {
      this(methodParameter, required, true);
   }

   public DependencyDescriptor(MethodParameter methodParameter, boolean required, boolean eager) {
      super(methodParameter);
      this.declaringClass = methodParameter.getDeclaringClass();
      if (methodParameter.getMethod() != null) {
         this.methodName = methodParameter.getMethod().getName();
      }

      this.parameterTypes = methodParameter.getExecutable().getParameterTypes();
      this.parameterIndex = methodParameter.getParameterIndex();
      this.containingClass = methodParameter.getContainingClass();
      this.required = required;
      this.eager = eager;
   }

   public DependencyDescriptor(Field field, boolean required) {
      this(field, required, true);
   }

   public DependencyDescriptor(Field field, boolean required, boolean eager) {
      super(field);
      this.declaringClass = field.getDeclaringClass();
      this.fieldName = field.getName();
      this.required = required;
      this.eager = eager;
   }

   public DependencyDescriptor(DependencyDescriptor original) {
      super(original);
      this.declaringClass = original.declaringClass;
      this.methodName = original.methodName;
      this.parameterTypes = original.parameterTypes;
      this.parameterIndex = original.parameterIndex;
      this.fieldName = original.fieldName;
      this.required = original.required;
      this.eager = original.eager;
      this.nestingLevel = original.nestingLevel;
      this.containingClass = original.containingClass;
   }

   public boolean isRequired() {
      if (!this.required) {
         return false;
      } else {
         return this.field == null
            ? !this.obtainMethodParameter().isOptional()
            : this.field.getType() != Optional.class && Nullness.forField(this.field) != Nullness.NULLABLE;
      }
   }

   public boolean isEager() {
      return this.eager;
   }

   public @Nullable Object resolveNotUnique(ResolvableType type, Map<String, Object> matchingBeans) throws BeansException {
      throw new NoUniqueBeanDefinitionException(type, matchingBeans.keySet());
   }

   public @Nullable Object resolveShortcut(BeanFactory beanFactory) throws BeansException {
      return null;
   }

   public Object resolveCandidate(String beanName, Class<?> requiredType, BeanFactory beanFactory) throws BeansException {
      try {
         return beanFactory.getBean(beanName, requiredType);
      } catch (BeanNotOfRequiredTypeException ex) {
         return beanFactory.getBean(beanName);
      }
   }

   public void increaseNestingLevel() {
      this.nestingLevel++;
      this.resolvableType = null;
      if (this.methodParameter != null) {
         this.methodParameter = this.methodParameter.nested();
      }
   }

   public void setContainingClass(Class<?> containingClass) {
      this.containingClass = containingClass;
      this.resolvableType = null;
      if (this.methodParameter != null) {
         this.methodParameter = this.methodParameter.withContainingClass(containingClass);
      }
   }

   public ResolvableType getResolvableType() {
      ResolvableType resolvableType = this.resolvableType;
      if (resolvableType == null) {
         resolvableType = this.field != null
            ? ResolvableType.forField(this.field, this.nestingLevel, this.containingClass)
            : ResolvableType.forMethodParameter(this.obtainMethodParameter());
         this.resolvableType = resolvableType;
      }

      return resolvableType;
   }

   public TypeDescriptor getTypeDescriptor() {
      TypeDescriptor typeDescriptor = this.typeDescriptor;
      if (typeDescriptor == null) {
         typeDescriptor = this.field != null
            ? new TypeDescriptor(this.getResolvableType(), this.getDependencyType(), this.getAnnotations())
            : new TypeDescriptor(this.obtainMethodParameter());
         this.typeDescriptor = typeDescriptor;
      }

      return typeDescriptor;
   }

   public boolean fallbackMatchAllowed() {
      return false;
   }

   public DependencyDescriptor forFallbackMatch() {
      return new DependencyDescriptor(this) {
         @Override
         public boolean fallbackMatchAllowed() {
            return true;
         }

         @Override
         public boolean usesStandardBeanLookup() {
            return true;
         }
      };
   }

   public void initParameterNameDiscovery(@Nullable ParameterNameDiscoverer parameterNameDiscoverer) {
      if (this.methodParameter != null) {
         this.methodParameter.initParameterNameDiscovery(parameterNameDiscoverer);
      }
   }

   public @Nullable String getDependencyName() {
      return this.field != null ? this.field.getName() : this.obtainMethodParameter().getParameterName();
   }

   public Class<?> getDependencyType() {
      if (this.field != null) {
         if (this.nestingLevel > 1) {
            Class<?> clazz = this.getResolvableType().getRawClass();
            return clazz != null ? clazz : Object.class;
         } else {
            return this.field.getType();
         }
      } else {
         return this.obtainMethodParameter().getNestedParameterType();
      }
   }

   public boolean supportsLazyResolution() {
      return true;
   }

   public boolean usesStandardBeanLookup() {
      return this.getClass() == DependencyDescriptor.class;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      if (this == other) {
         return true;
      } else {
         return !super.equals(other)
            ? false
            : other instanceof DependencyDescriptor otherDesc
               && this.required == otherDesc.required
               && this.eager == otherDesc.eager
               && this.nestingLevel == otherDesc.nestingLevel
               && this.containingClass == otherDesc.containingClass;
      }
   }

   @Override
   public int hashCode() {
      return 31 * super.hashCode() + ObjectUtils.nullSafeHashCode(this.containingClass);
   }

   private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
      ois.defaultReadObject();

      try {
         if (this.fieldName != null) {
            this.field = this.declaringClass.getDeclaredField(this.fieldName);
         } else {
            if (this.methodName != null) {
               this.methodParameter = new MethodParameter(this.declaringClass.getDeclaredMethod(this.methodName, this.parameterTypes), this.parameterIndex);
            } else {
               this.methodParameter = new MethodParameter(this.declaringClass.getDeclaredConstructor(this.parameterTypes), this.parameterIndex);
            }

            for (int i = 1; i < this.nestingLevel; i++) {
               this.methodParameter = this.methodParameter.nested();
            }
         }
      } catch (Throwable ex) {
         throw new IllegalStateException("Could not find original class structure", ex);
      }
   }
}
