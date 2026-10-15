package org.springframework.boot.context.config;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.origin.OriginProvider;

public abstract class ConfigDataNotFoundException extends ConfigDataException implements OriginProvider {
   ConfigDataNotFoundException(String message, @Nullable Throwable cause) {
      super(message, cause);
   }

   public abstract String getReferenceDescription();
}
