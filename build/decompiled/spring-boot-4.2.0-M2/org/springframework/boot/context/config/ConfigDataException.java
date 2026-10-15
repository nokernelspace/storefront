package org.springframework.boot.context.config;

import org.jspecify.annotations.Nullable;

public abstract class ConfigDataException extends RuntimeException {
   protected ConfigDataException(String message, @Nullable Throwable cause) {
      super(message, cause);
   }
}
