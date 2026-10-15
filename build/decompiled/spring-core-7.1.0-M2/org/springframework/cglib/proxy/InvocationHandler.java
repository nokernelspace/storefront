package org.springframework.cglib.proxy;

import java.lang.reflect.Method;

public interface InvocationHandler extends Callback {
   Object invoke(Object proxy, Method method, Object[] args) throws Throwable;
}
