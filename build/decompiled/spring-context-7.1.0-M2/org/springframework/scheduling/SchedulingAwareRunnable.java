package org.springframework.scheduling;

import org.jspecify.annotations.Nullable;

public interface SchedulingAwareRunnable extends Runnable {
   default boolean isLongLived() {
      return false;
   }

   default @Nullable String getQualifier() {
      return null;
   }
}
