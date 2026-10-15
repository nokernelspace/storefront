package org.springframework.cglib.proxy;

import java.lang.reflect.Method;

public interface MethodInterceptor extends Callback {
   Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable;
}
