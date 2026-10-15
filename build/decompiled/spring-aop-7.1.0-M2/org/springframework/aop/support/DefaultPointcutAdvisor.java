package org.springframework.aop.support;

import java.io.Serializable;
import org.aopalliance.aop.Advice;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.Pointcut;

public class DefaultPointcutAdvisor extends AbstractGenericPointcutAdvisor implements Serializable {
   private Pointcut pointcut = Pointcut.TRUE;

   public DefaultPointcutAdvisor() {
   }

   public DefaultPointcutAdvisor(Advice advice) {
      this(Pointcut.TRUE, advice);
   }

   public DefaultPointcutAdvisor(Pointcut pointcut, Advice advice) {
      this.pointcut = pointcut;
      this.setAdvice(advice);
   }

   public void setPointcut(@Nullable Pointcut pointcut) {
      this.pointcut = pointcut != null ? pointcut : Pointcut.TRUE;
   }

   @Override
   public Pointcut getPointcut() {
      return this.pointcut;
   }

   @Override
   public String toString() {
      return this.getClass().getName() + ": pointcut [" + this.getPointcut() + "]; advice [" + this.getAdvice() + "]";
   }
}
