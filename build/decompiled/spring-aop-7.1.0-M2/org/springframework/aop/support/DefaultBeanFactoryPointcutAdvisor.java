package org.springframework.aop.support;

import org.jspecify.annotations.Nullable;
import org.springframework.aop.Pointcut;

public class DefaultBeanFactoryPointcutAdvisor extends AbstractBeanFactoryPointcutAdvisor {
   private Pointcut pointcut = Pointcut.TRUE;

   public void setPointcut(@Nullable Pointcut pointcut) {
      this.pointcut = pointcut != null ? pointcut : Pointcut.TRUE;
   }

   @Override
   public Pointcut getPointcut() {
      return this.pointcut;
   }

   @Override
   public String toString() {
      return this.getClass().getName() + ": pointcut [" + this.getPointcut() + "]; advice bean '" + this.getAdviceBeanName() + "'";
   }
}
