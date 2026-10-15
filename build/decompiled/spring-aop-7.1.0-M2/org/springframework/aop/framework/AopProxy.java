package org.springframework.aop.framework;

import org.jspecify.annotations.Nullable;

public interface AopProxy {
   Object getProxy();

   Object getProxy(@Nullable ClassLoader classLoader);

   Class<?> getProxyClass(@Nullable ClassLoader classLoader);
}
