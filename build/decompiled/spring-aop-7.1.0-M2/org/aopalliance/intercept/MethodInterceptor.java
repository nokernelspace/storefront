package org.aopalliance.intercept;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface MethodInterceptor extends Interceptor {
   @Nullable Object invoke(MethodInvocation invocation) throws Throwable;
}
