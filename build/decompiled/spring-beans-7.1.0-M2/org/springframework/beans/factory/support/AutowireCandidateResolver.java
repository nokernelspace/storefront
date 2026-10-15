package org.springframework.beans.factory.support;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.DependencyDescriptor;

public interface AutowireCandidateResolver {
   default boolean isAutowireCandidate(BeanDefinitionHolder bdHolder, DependencyDescriptor descriptor) {
      return bdHolder.getBeanDefinition().isAutowireCandidate();
   }

   default boolean isRequired(DependencyDescriptor descriptor) {
      return descriptor.isRequired();
   }

   default boolean hasQualifier(DependencyDescriptor descriptor) {
      return false;
   }

   default @Nullable String getSuggestedName(DependencyDescriptor descriptor) {
      return null;
   }

   default @Nullable Object getSuggestedValue(DependencyDescriptor descriptor) {
      return null;
   }

   default @Nullable Object getLazyResolutionProxyIfNecessary(DependencyDescriptor descriptor, @Nullable String beanName) {
      return null;
   }

   default @Nullable Class<?> getLazyResolutionProxyClass(DependencyDescriptor descriptor, @Nullable String beanName) {
      return null;
   }

   default AutowireCandidateResolver cloneIfNecessary() {
      return BeanUtils.instantiateClass((Class<AutowireCandidateResolver>)this.getClass());
   }
}
