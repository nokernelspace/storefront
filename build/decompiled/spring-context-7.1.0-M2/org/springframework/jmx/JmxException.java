package org.springframework.jmx;

import org.springframework.core.NestedRuntimeException;

public class JmxException extends NestedRuntimeException {
   public JmxException(String msg) {
      super(msg);
   }

   public JmxException(String msg, Throwable cause) {
      super(msg, cause);
   }
}
