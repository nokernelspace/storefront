package org.springframework.context.annotation;

class ConflictingBeanDefinitionException extends IllegalStateException {
   public ConflictingBeanDefinitionException(String message) {
      super(message);
   }
}
