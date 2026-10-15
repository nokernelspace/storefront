package org.springframework.context.annotation;

import org.jspecify.annotations.Nullable;
import org.springframework.core.type.MethodMetadata;

public class FullyQualifiedConfigurationBeanNameGenerator extends FullyQualifiedAnnotationBeanNameGenerator implements ConfigurationBeanNameGenerator {
   public static final FullyQualifiedConfigurationBeanNameGenerator INSTANCE = new FullyQualifiedConfigurationBeanNameGenerator();

   @Override
   public String deriveBeanName(MethodMetadata beanMethod, @Nullable String beanName) {
      return beanName != null ? beanName : beanMethod.getDeclaringClassName() + "." + beanMethod.getMethodName();
   }
}
