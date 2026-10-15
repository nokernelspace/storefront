package org.springframework.beans.factory;

import org.springframework.beans.FatalBeanException;

public class FactoryBeanNotInitializedException extends FatalBeanException {
   public FactoryBeanNotInitializedException() {
      super("FactoryBean is not fully initialized yet");
   }

   public FactoryBeanNotInitializedException(String msg) {
      super(msg);
   }
}
