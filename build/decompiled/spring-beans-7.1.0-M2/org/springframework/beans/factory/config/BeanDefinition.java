package org.springframework.beans.factory.config;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanMetadataElement;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.core.AttributeAccessor;
import org.springframework.core.ResolvableType;

public interface BeanDefinition extends AttributeAccessor, BeanMetadataElement {
   String SCOPE_SINGLETON = "singleton";
   String SCOPE_PROTOTYPE = "prototype";
   int ROLE_APPLICATION = 0;
   int ROLE_SUPPORT = 1;
   int ROLE_INFRASTRUCTURE = 2;

   void setParentName(@Nullable String parentName);

   @Nullable String getParentName();

   void setBeanClassName(@Nullable String beanClassName);

   @Nullable String getBeanClassName();

   void setScope(@Nullable String scope);

   @Nullable String getScope();

   void setLazyInit(boolean lazyInit);

   boolean isLazyInit();

   void setDependsOn(String @Nullable ... dependsOn);

   String @Nullable [] getDependsOn();

   void setAutowireCandidate(boolean autowireCandidate);

   boolean isAutowireCandidate();

   void setPrimary(boolean primary);

   boolean isPrimary();

   void setFallback(boolean fallback);

   boolean isFallback();

   void setFactoryBeanName(@Nullable String factoryBeanName);

   @Nullable String getFactoryBeanName();

   void setFactoryMethodName(@Nullable String factoryMethodName);

   @Nullable String getFactoryMethodName();

   ConstructorArgumentValues getConstructorArgumentValues();

   default boolean hasConstructorArgumentValues() {
      return !this.getConstructorArgumentValues().isEmpty();
   }

   MutablePropertyValues getPropertyValues();

   default boolean hasPropertyValues() {
      return !this.getPropertyValues().isEmpty();
   }

   void setInitMethodName(@Nullable String initMethodName);

   @Nullable String getInitMethodName();

   void setDestroyMethodName(@Nullable String destroyMethodName);

   @Nullable String getDestroyMethodName();

   void setRole(int role);

   int getRole();

   void setDescription(@Nullable String description);

   @Nullable String getDescription();

   ResolvableType getResolvableType();

   boolean isSingleton();

   boolean isPrototype();

   boolean isAbstract();

   @Nullable String getResourceDescription();

   @Nullable BeanDefinition getOriginatingBeanDefinition();
}
