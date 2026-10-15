package org.springframework.aot.hint;

import org.jspecify.annotations.Nullable;

public enum ExecutableMode {
   @Deprecated(since = "7.0", forRemoval = true)
   INTROSPECT,
   INVOKE;

   boolean includes(@Nullable ExecutableMode other) {
      return other == null || this.ordinal() >= other.ordinal();
   }
}
