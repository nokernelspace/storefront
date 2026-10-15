package org.springframework.jmx.export;

public class UnableToRegisterMBeanException extends MBeanExportException {
   public UnableToRegisterMBeanException(String msg) {
      super(msg);
   }

   public UnableToRegisterMBeanException(String msg, Throwable cause) {
      super(msg, cause);
   }
}
