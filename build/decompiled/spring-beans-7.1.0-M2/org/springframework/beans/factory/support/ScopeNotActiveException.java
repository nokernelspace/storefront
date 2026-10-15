package org.springframework.beans.factory.support;

import org.springframework.beans.factory.BeanCreationException;

public class ScopeNotActiveException extends BeanCreationException {
   public ScopeNotActiveException(String beanName, String scopeName, IllegalStateException cause) {
      super(
         beanName,
         "Scope '"
            + scopeName
            + "' is not active for the current thread; consider defining a scoped proxy for this bean if you intend to refer to it from a singleton",
         cause
      );
   }
}
