package org.springframework.beans.factory.support;

import java.lang.reflect.Method;

public interface MethodReplacer {
   Object reimplement(Object obj, Method method, Object[] args) throws Throwable;
}
