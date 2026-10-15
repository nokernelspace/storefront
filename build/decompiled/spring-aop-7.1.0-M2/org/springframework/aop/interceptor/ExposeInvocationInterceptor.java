package org.springframework.aop.interceptor;

import java.io.Serializable;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.Advisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.core.NamedThreadLocal;
import org.springframework.core.PriorityOrdered;

public final class ExposeInvocationInterceptor implements MethodInterceptor, PriorityOrdered, Serializable {
   public static final ExposeInvocationInterceptor INSTANCE = new ExposeInvocationInterceptor();
   public static final Advisor ADVISOR = new DefaultPointcutAdvisor(INSTANCE) {
      @Override
      public String toString() {
         return ExposeInvocationInterceptor.class.getName() + ".ADVISOR";
      }
   };
   private static final ThreadLocal<MethodInvocation> invocation = new NamedThreadLocal("Current AOP method invocation");

   public static MethodInvocation currentInvocation() throws IllegalStateException {
      MethodInvocation mi = invocation.get();
      if (mi == null) {
         throw new IllegalStateException(
            "No MethodInvocation found: Check that an AOP invocation is in progress and that the ExposeInvocationInterceptor is upfront in the interceptor chain. Specifically, note that advices with order HIGHEST_PRECEDENCE will execute before ExposeInvocationInterceptor! In addition, ExposeInvocationInterceptor and ExposeInvocationInterceptor.currentInvocation() must be invoked from the same thread."
         );
      } else {
         return mi;
      }
   }

   private ExposeInvocationInterceptor() {
   }

   @Override
   public @Nullable Object invoke(MethodInvocation mi) throws Throwable {
      MethodInvocation oldInvocation = invocation.get();
      invocation.set(mi);

      try {
         return mi.proceed();
      } finally {
         invocation.set(oldInvocation);
      }
   }

   public int getOrder() {
      return -2147483647;
   }

   private Object readResolve() {
      return INSTANCE;
   }
}
