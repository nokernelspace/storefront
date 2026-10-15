package org.springframework.aop.interceptor;

import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;

public class DebugInterceptor extends SimpleTraceInterceptor {
   private volatile long count;

   public DebugInterceptor() {
   }

   public DebugInterceptor(boolean useDynamicLogger) {
      this.setUseDynamicLogger(useDynamicLogger);
   }

   @Override
   public @Nullable Object invoke(MethodInvocation invocation) throws Throwable {
      synchronized (this) {
         this.count++;
      }

      return super.invoke(invocation);
   }

   @Override
   protected String getInvocationDescription(MethodInvocation invocation) {
      return invocation + "; count=" + this.count;
   }

   public long getCount() {
      return this.count;
   }

   public synchronized void resetCount() {
      this.count = 0L;
   }
}
