package org.springframework.aop;

import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;

public interface ProxyMethodInvocation extends MethodInvocation {
   Object getProxy();

   MethodInvocation invocableClone();

   MethodInvocation invocableClone(@Nullable Object... arguments);

   void setArguments(@Nullable Object... arguments);

   void setUserAttribute(String key, @Nullable Object value);

   @Nullable Object getUserAttribute(String key);
}
