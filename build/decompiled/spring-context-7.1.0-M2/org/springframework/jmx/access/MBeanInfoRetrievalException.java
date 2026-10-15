package org.springframework.jmx.access;

import org.springframework.jmx.JmxException;

public class MBeanInfoRetrievalException extends JmxException {
   public MBeanInfoRetrievalException(String msg) {
      super(msg);
   }

   public MBeanInfoRetrievalException(String msg, Throwable cause) {
      super(msg, cause);
   }
}
