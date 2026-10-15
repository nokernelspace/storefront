package org.springframework.aot.hint;

import org.jspecify.annotations.Nullable;
import org.springframework.util.ClassUtils;

public interface ConditionalHint {
   @Nullable TypeReference getReachableType();

   default boolean conditionMatches(ClassLoader classLoader) {
      TypeReference reachableType = this.getReachableType();
      return reachableType != null ? ClassUtils.isPresent(reachableType.getCanonicalName(), classLoader) : true;
   }
}
