package org.springframework.beans.factory.wiring;

import org.jspecify.annotations.Nullable;

public interface BeanWiringInfoResolver {
   @Nullable BeanWiringInfo resolveWiringInfo(Object beanInstance);
}
