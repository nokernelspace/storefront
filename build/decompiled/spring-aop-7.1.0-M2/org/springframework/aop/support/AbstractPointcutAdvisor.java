package org.springframework.aop.support;

import java.io.Serializable;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.core.Ordered;
import org.springframework.util.ObjectUtils;

public abstract class AbstractPointcutAdvisor implements PointcutAdvisor, Ordered, Serializable {
   private @Nullable Integer order;

   public void setOrder(int order) {
      this.order = order;
   }

   public int getOrder() {
      if (this.order != null) {
         return this.order;
      } else {
         return this.getAdvice() instanceof Ordered ordered ? ordered.getOrder() : Integer.MAX_VALUE;
      }
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof PointcutAdvisor otherAdvisor
            && ObjectUtils.nullSafeEquals(this.getAdvice(), otherAdvisor.getAdvice())
            && ObjectUtils.nullSafeEquals(this.getPointcut(), otherAdvisor.getPointcut());
   }

   @Override
   public int hashCode() {
      return PointcutAdvisor.class.hashCode();
   }
}
