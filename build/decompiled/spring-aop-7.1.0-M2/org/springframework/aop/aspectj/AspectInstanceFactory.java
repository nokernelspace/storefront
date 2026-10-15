package org.springframework.aop.aspectj;

import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;

public interface AspectInstanceFactory extends Ordered {
   Object getAspectInstance();

   @Nullable ClassLoader getAspectClassLoader();
}
