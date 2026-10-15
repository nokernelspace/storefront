package org.springframework.aop.interceptor;

import java.lang.reflect.Method;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;

public abstract class AbstractMonitoringInterceptor extends AbstractTraceInterceptor {
   private String prefix = "";
   private String suffix = "";
   private boolean logTargetClassInvocation = false;

   public void setPrefix(@Nullable String prefix) {
      this.prefix = prefix != null ? prefix : "";
   }

   protected String getPrefix() {
      return this.prefix;
   }

   public void setSuffix(@Nullable String suffix) {
      this.suffix = suffix != null ? suffix : "";
   }

   protected String getSuffix() {
      return this.suffix;
   }

   public void setLogTargetClassInvocation(boolean logTargetClassInvocation) {
      this.logTargetClassInvocation = logTargetClassInvocation;
   }

   protected String createInvocationTraceName(MethodInvocation invocation) {
      Method method = invocation.getMethod();
      Class<?> clazz = method.getDeclaringClass();
      if (this.logTargetClassInvocation && clazz.isInstance(invocation.getThis())) {
         clazz = invocation.getThis().getClass();
      }

      String className = clazz.getName();
      return this.getPrefix() + className + "." + method.getName() + this.getSuffix();
   }
}
