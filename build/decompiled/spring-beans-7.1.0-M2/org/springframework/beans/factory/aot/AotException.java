package org.springframework.beans.factory.aot;

import org.jspecify.annotations.Nullable;

public abstract class AotException extends RuntimeException {
   protected AotException(@Nullable String msg, @Nullable Throwable cause) {
      super(msg, cause);
   }
}
