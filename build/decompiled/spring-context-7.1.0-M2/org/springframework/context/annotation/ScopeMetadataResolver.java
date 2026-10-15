package org.springframework.context.annotation;

import org.springframework.beans.factory.config.BeanDefinition;

@FunctionalInterface
public interface ScopeMetadataResolver {
   ScopeMetadata resolveScopeMetadata(BeanDefinition definition);
}
