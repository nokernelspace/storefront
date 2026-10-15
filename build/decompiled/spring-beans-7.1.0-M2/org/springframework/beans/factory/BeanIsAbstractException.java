package org.springframework.beans.factory;

public class BeanIsAbstractException extends BeanCreationException {
   public BeanIsAbstractException(String beanName) {
      super(beanName, "Bean definition is abstract");
   }
}
