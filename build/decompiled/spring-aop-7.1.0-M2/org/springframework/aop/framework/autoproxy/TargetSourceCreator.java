package org.springframework.aop.framework.autoproxy;

import org.jspecify.annotations.Nullable;
import org.springframework.aop.TargetSource;

@FunctionalInterface
public interface TargetSourceCreator {
   @Nullable TargetSource getTargetSource(Class<?> beanClass, String beanName);
}
