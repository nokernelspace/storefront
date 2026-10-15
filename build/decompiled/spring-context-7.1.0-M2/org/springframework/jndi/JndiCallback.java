package org.springframework.jndi;

import javax.naming.Context;
import javax.naming.NamingException;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface JndiCallback<T> {
   @Nullable T doInContext(Context ctx) throws NamingException;
}
