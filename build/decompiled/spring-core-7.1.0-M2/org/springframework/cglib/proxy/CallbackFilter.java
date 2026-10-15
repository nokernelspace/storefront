package org.springframework.cglib.proxy;

import java.lang.reflect.Method;

public interface CallbackFilter {
   int accept(Method method);

   @Override
   boolean equals(Object o);
}
