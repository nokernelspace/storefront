package org.springframework.aop.aspectj;

import org.aopalliance.aop.Advice;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.Pointcut;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.core.Ordered;
import org.springframework.util.Assert;

public class AspectJPointcutAdvisor implements PointcutAdvisor, Ordered {
   private final AbstractAspectJAdvice advice;
   private final Pointcut pointcut;
   private @Nullable Integer order;

   public AspectJPointcutAdvisor(AbstractAspectJAdvice advice) {
      Assert.notNull(advice, "Advice must not be null");
      this.advice = advice;
      this.pointcut = advice.buildSafePointcut();
   }

   public void setOrder(int order) {
      this.order = order;
   }

   public int getOrder() {
      return this.order != null ? this.order : this.advice.getOrder();
   }

   @Override
   public Advice getAdvice() {
      return this.advice;
   }

   @Override
   public Pointcut getPointcut() {
      return this.pointcut;
   }

   public String getAspectName() {
      return this.advice.getAspectName();
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof AspectJPointcutAdvisor that && this.advice.equals(that.advice);
   }

   @Override
   public int hashCode() {
      return AspectJPointcutAdvisor.class.hashCode() * 29 + this.advice.hashCode();
   }
}
