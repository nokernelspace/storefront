package org.springframework.aop;

import org.jspecify.annotations.Nullable;

public interface TargetSource extends TargetClassAware {
   @Override
   @Nullable Class<?> getTargetClass();

   default boolean isStatic() {
      return false;
   }

   @Nullable Object getTarget() throws Exception;

   default void releaseTarget(Object target) throws Exception {
   }
}
