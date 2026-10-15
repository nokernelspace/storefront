package org.aopalliance.intercept;

public interface ConstructorInterceptor extends Interceptor {
   Object construct(ConstructorInvocation invocation) throws Throwable;
}
