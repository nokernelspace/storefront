package org.springframework.jmx.access;

import org.springframework.jmx.JmxException;

public class InvocationFailureException extends JmxException {
   public InvocationFailureException(String msg) {
      super(msg);
   }

   public InvocationFailureException(String msg, Throwable cause) {
      super(msg, cause);
   }
}
