package io.micrometer.common.util.internal.logging;

import org.jspecify.annotations.Nullable;

final class FormattingTuple {
   private final @Nullable String message;
   private final @Nullable Throwable throwable;

   FormattingTuple(@Nullable String message, @Nullable Throwable throwable) {
      this.message = message;
      this.throwable = throwable;
   }

   public @Nullable String getMessage() {
      return this.message;
   }

   public @Nullable Throwable getThrowable() {
      return this.throwable;
   }
}
