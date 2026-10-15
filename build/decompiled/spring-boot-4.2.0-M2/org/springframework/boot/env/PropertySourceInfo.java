package org.springframework.boot.env;

import org.jspecify.annotations.Nullable;

public interface PropertySourceInfo {
   default boolean isImmutable() {
      return false;
   }

   default @Nullable String getPrefix() {
      return null;
   }
}
