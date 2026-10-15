package org.springframework.aop.interceptor;

import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StopWatch;

public class PerformanceMonitorInterceptor extends AbstractMonitoringInterceptor {
   public PerformanceMonitorInterceptor() {
   }

   public PerformanceMonitorInterceptor(boolean useDynamicLogger) {
      this.setUseDynamicLogger(useDynamicLogger);
   }

   @Override
   protected @Nullable Object invokeUnderTrace(MethodInvocation invocation, Log logger) throws Throwable {
      String name = this.createInvocationTraceName(invocation);
      StopWatch stopWatch = new StopWatch(name);
      stopWatch.start(name);

      try {
         return invocation.proceed();
      } finally {
         stopWatch.stop();
         this.writeToLog(logger, stopWatch.shortSummary());
      }
   }
}
