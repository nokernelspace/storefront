package org.springframework.aop.config;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanReference;
import org.springframework.beans.factory.parsing.CompositeComponentDefinition;

public class AspectComponentDefinition extends CompositeComponentDefinition {
   private final BeanDefinition[] beanDefinitions;
   private final BeanReference[] beanReferences;

   public AspectComponentDefinition(
      String aspectName, BeanDefinition @Nullable [] beanDefinitions, BeanReference @Nullable [] beanReferences, @Nullable Object source
   ) {
      super(aspectName, source);
      this.beanDefinitions = beanDefinitions != null ? beanDefinitions : new BeanDefinition[0];
      this.beanReferences = beanReferences != null ? beanReferences : new BeanReference[0];
   }

   public BeanDefinition[] getBeanDefinitions() {
      return this.beanDefinitions;
   }

   public BeanReference[] getBeanReferences() {
      return this.beanReferences;
   }
}
