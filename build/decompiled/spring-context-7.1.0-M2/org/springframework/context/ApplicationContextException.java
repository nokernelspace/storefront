package org.springframework.context;

import org.springframework.beans.FatalBeanException;

public class ApplicationContextException extends FatalBeanException {
   public ApplicationContextException(String msg) {
      super(msg);
   }

   public ApplicationContextException(String msg, Throwable cause) {
      super(msg, cause);
   }
}
