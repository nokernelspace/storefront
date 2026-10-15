package org.springframework.aop.framework.autoproxy;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.TargetSource;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.util.Assert;
import org.springframework.util.PatternMatchUtils;

public class BeanNameAutoProxyCreator extends AbstractAutoProxyCreator {
   private static final String[] NO_ALIASES = new String[0];
   private @Nullable List<String> beanNames;

   public void setBeanNames(String... beanNames) {
      Assert.notEmpty(beanNames, "'beanNames' must not be empty");
      this.beanNames = new ArrayList<>(beanNames.length);

      for (String mappedName : beanNames) {
         this.beanNames.add(mappedName.strip());
      }
   }

   @Override
   protected @Nullable TargetSource getCustomTargetSource(Class<?> beanClass, String beanName) {
      return this.isSupportedBeanName(beanClass, beanName) ? super.getCustomTargetSource(beanClass, beanName) : null;
   }

   @Override
   protected Object @Nullable [] getAdvicesAndAdvisorsForBean(Class<?> beanClass, String beanName, @Nullable TargetSource targetSource) {
      return this.isSupportedBeanName(beanClass, beanName) ? PROXY_WITHOUT_ADDITIONAL_INTERCEPTORS : DO_NOT_PROXY;
   }

   private boolean isSupportedBeanName(Class<?> beanClass, String beanName) {
      if (this.beanNames != null) {
         boolean isFactoryBean = FactoryBean.class.isAssignableFrom(beanClass);

         for (String mappedName : this.beanNames) {
            if (isFactoryBean) {
               if (mappedName.isEmpty() || mappedName.charAt(0) != '&') {
                  continue;
               }

               mappedName = mappedName.substring(1);
            }

            if (this.isMatch(beanName, mappedName)) {
               return true;
            }
         }

         BeanFactory beanFactory = this.getBeanFactory();
         String[] aliases = beanFactory != null ? beanFactory.getAliases(beanName) : NO_ALIASES;

         for (String alias : aliases) {
            for (String mappedName : this.beanNames) {
               if (this.isMatch(alias, mappedName)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   protected boolean isMatch(String beanName, String mappedName) {
      return PatternMatchUtils.simpleMatch(mappedName, beanName);
   }
}
