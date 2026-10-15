package org.springframework.beans.factory.parsing;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanReference;

public abstract class AbstractComponentDefinition implements ComponentDefinition {
   @Override
   public String getDescription() {
      return this.getName();
   }

   @Override
   public BeanDefinition[] getBeanDefinitions() {
      return new BeanDefinition[0];
   }

   @Override
   public BeanDefinition[] getInnerBeanDefinitions() {
      return new BeanDefinition[0];
   }

   @Override
   public BeanReference[] getBeanReferences() {
      return new BeanReference[0];
   }

   @Override
   public String toString() {
      return this.getDescription();
   }
}
