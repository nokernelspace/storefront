package org.springframework.core;

import org.jspecify.annotations.Nullable;

public abstract class NestedExceptionUtils {
   @Deprecated(since = "6.0")
   public static @Nullable String buildMessage(@Nullable String message, @Nullable Throwable cause) {
      if (cause == null) {
         return message;
      }

      StringBuilder sb = new StringBuilder(64);
      if (message != null) {
         sb.append(message).append("; ");
      }

      sb.append("nested exception is ").append(cause);
      return sb.toString();
   }

   public static @Nullable Throwable getRootCause(@Nullable Throwable original) {
      if (original == null) {
         return null;
      }

      Throwable rootCause = null;

      for (Throwable cause = original.getCause(); cause != null && cause != rootCause; cause = cause.getCause()) {
         rootCause = cause;
      }

      return rootCause;
   }

   public static Throwable getMostSpecificCause(Throwable original) {
      Throwable rootCause = getRootCause(original);
      return rootCause != null ? rootCause : original;
   }
}
