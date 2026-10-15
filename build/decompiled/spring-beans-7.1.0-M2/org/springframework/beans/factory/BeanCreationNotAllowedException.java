package org.springframework.beans.factory;

public class BeanCreationNotAllowedException extends BeanCreationException {
   public BeanCreationNotAllowedException(String beanName, String msg) {
      super(beanName, msg);
   }
}
