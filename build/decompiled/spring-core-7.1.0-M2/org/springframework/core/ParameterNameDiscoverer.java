package org.springframework.core;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;

public interface ParameterNameDiscoverer {
   @Nullable String @Nullable [] getParameterNames(Method method);

   @Nullable String @Nullable [] getParameterNames(Constructor<?> ctor);
}
