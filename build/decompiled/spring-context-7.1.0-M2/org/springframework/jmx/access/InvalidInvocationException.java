package org.springframework.jmx.access;

import javax.management.JMRuntimeException;
import org.jspecify.annotations.Nullable;

public class InvalidInvocationException extends JMRuntimeException {
   public InvalidInvocationException(@Nullable String msg) {
      super(msg);
   }
}
