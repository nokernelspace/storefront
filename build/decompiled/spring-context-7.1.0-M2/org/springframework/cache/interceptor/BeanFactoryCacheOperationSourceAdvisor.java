package org.springframework.cache.interceptor;

import org.springframework.aop.ClassFilter;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractBeanFactoryPointcutAdvisor;

public class BeanFactoryCacheOperationSourceAdvisor extends AbstractBeanFactoryPointcutAdvisor {
   private final CacheOperationSourcePointcut pointcut = new CacheOperationSourcePointcut();

   public void setCacheOperationSource(CacheOperationSource cacheOperationSource) {
      this.pointcut.setCacheOperationSource(cacheOperationSource);
   }

   public void setClassFilter(ClassFilter classFilter) {
      this.pointcut.setClassFilter(classFilter);
   }

   public Pointcut getPointcut() {
      return this.pointcut;
   }
}
