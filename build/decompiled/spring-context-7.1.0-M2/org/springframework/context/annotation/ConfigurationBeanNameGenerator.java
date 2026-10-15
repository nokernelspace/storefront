package org.springframework.context.annotation;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.core.type.MethodMetadata;

public interface ConfigurationBeanNameGenerator extends BeanNameGenerator {
   String deriveBeanName(MethodMetadata beanMethod, @Nullable String beanName);
}
