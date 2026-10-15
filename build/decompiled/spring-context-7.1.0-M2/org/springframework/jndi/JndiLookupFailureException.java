package org.springframework.jndi;

import javax.naming.NamingException;
import org.springframework.core.NestedRuntimeException;

public class JndiLookupFailureException extends NestedRuntimeException {
   public JndiLookupFailureException(String msg, NamingException cause) {
      super(msg, cause);
   }
}
