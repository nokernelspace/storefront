package org.springframework.beans.factory.support;

import org.springframework.beans.FatalBeanException;

public class BeanDefinitionValidationException extends FatalBeanException {
   public BeanDefinitionValidationException(String msg) {
      super(msg);
   }

   public BeanDefinitionValidationException(String msg, Throwable cause) {
      super(msg, cause);
   }
}
