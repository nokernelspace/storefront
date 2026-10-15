package org.springframework.jmx.access;

import org.springframework.jmx.JmxException;

public class MBeanConnectFailureException extends JmxException {
   public MBeanConnectFailureException(String msg, Throwable cause) {
      super(msg, cause);
   }
}
