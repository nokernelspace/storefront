package org.springframework.aop;

import org.jspecify.annotations.Nullable;

public interface TargetClassAware {
   @Nullable Class<?> getTargetClass();
}
