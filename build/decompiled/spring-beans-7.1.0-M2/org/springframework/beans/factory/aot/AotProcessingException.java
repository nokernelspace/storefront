package org.springframework.beans.factory.aot;

import org.jspecify.annotations.Nullable;

public class AotProcessingException extends AotException {
   public AotProcessingException(String msg, @Nullable Throwable cause) {
      super(msg, cause);
   }
}
