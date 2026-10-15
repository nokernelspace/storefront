package org.springframework.beans.factory.aot;

import org.springframework.aot.generate.GenerationContext;

@FunctionalInterface
public interface BeanFactoryInitializationAotContribution {
   void applyTo(GenerationContext generationContext, BeanFactoryInitializationCode beanFactoryInitializationCode);
}
