package org.springframework.beans;

import org.jspecify.annotations.Nullable;

public class FatalBeanException extends BeansException {
   public FatalBeanException(String msg) {
      super(msg);
   }

   public FatalBeanException(String msg, @Nullable Throwable cause) {
      super(msg, cause);
   }
}
