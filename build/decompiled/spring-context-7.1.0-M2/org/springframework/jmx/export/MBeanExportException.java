package org.springframework.jmx.export;

import org.springframework.jmx.JmxException;

public class MBeanExportException extends JmxException {
   public MBeanExportException(String msg) {
      super(msg);
   }

   public MBeanExportException(String msg, Throwable cause) {
      super(msg, cause);
   }
}
