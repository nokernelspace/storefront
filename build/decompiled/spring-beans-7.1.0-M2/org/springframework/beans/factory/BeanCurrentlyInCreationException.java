package org.springframework.beans.factory;

public class BeanCurrentlyInCreationException extends BeanCreationException {
   public BeanCurrentlyInCreationException(String beanName) {
      super(beanName, "Requested bean is currently in creation: Is there an unresolvable circular reference or an asynchronous initialization dependency?");
   }

   public BeanCurrentlyInCreationException(String beanName, String msg) {
      super(beanName, msg);
   }
}
