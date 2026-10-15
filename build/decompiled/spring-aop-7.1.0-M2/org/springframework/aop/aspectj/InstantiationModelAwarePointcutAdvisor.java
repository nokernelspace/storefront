package org.springframework.aop.aspectj;

import org.springframework.aop.PointcutAdvisor;

public interface InstantiationModelAwarePointcutAdvisor extends PointcutAdvisor {
   boolean isLazy();

   boolean isAdviceInstantiated();
}
