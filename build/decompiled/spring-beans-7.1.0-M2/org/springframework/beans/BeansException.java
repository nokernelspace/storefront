package org.springframework.beans;

import org.jspecify.annotations.Nullable;
import org.springframework.core.NestedRuntimeException;

public abstract class BeansException extends NestedRuntimeException {
   public BeansException(String msg) {
      super(msg);
   }

   public BeansException(@Nullable String msg, @Nullable Throwable cause) {
      super(msg, cause);
   }
}
