package org.springframework.core;

import org.jspecify.annotations.Nullable;

public abstract class NestedRuntimeException extends RuntimeException {
   private static final long serialVersionUID = 5439915454935047936L;

   public NestedRuntimeException(@Nullable String msg) {
      super(msg);
   }

   public NestedRuntimeException(@Nullable String msg, @Nullable Throwable cause) {
      super(msg, cause);
   }

   public @Nullable Throwable getRootCause() {
      return NestedExceptionUtils.getRootCause(this);
   }

   public Throwable getMostSpecificCause() {
      Throwable rootCause = this.getRootCause();
      return rootCause != null ? rootCause : this;
   }

   public boolean contains(@Nullable Class<?> exType) {
      if (exType == null) {
         return false;
      } else if (exType.isInstance(this)) {
         return true;
      } else {
         Throwable cause = this.getCause();
         if (cause == this) {
            return false;
         } else if (cause instanceof NestedRuntimeException exception) {
            return exception.contains(exType);
         } else {
            while (cause != null) {
               if (exType.isInstance(cause)) {
                  return true;
               }

               if (cause.getCause() == cause) {
                  break;
               }

               cause = cause.getCause();
            }

            return false;
         }
      }
   }
}
