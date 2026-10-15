package org.springframework.aop.framework.autoproxy;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanNameAware;

public class DefaultAdvisorAutoProxyCreator extends AbstractAdvisorAutoProxyCreator implements BeanNameAware {
   public static final String SEPARATOR = ".";
   private boolean usePrefix = false;
   private @Nullable String advisorBeanNamePrefix;

   public void setUsePrefix(boolean usePrefix) {
      this.usePrefix = usePrefix;
   }

   public boolean isUsePrefix() {
      return this.usePrefix;
   }

   public void setAdvisorBeanNamePrefix(@Nullable String advisorBeanNamePrefix) {
      this.advisorBeanNamePrefix = advisorBeanNamePrefix;
   }

   public @Nullable String getAdvisorBeanNamePrefix() {
      return this.advisorBeanNamePrefix;
   }

   public void setBeanName(String name) {
      if (this.advisorBeanNamePrefix == null) {
         this.advisorBeanNamePrefix = name + ".";
      }
   }

   @Override
   protected boolean isEligibleAdvisorBean(String beanName) {
      if (!this.isUsePrefix()) {
         return true;
      }

      String prefix = this.getAdvisorBeanNamePrefix();
      return prefix != null && beanName.startsWith(prefix);
   }
}
