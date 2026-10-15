package org.springframework.aop.framework.autoproxy;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.Advisor;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.util.Assert;

public class BeanFactoryAdvisorRetrievalHelper {
   private static final Log logger = LogFactory.getLog(BeanFactoryAdvisorRetrievalHelper.class);
   private final ConfigurableListableBeanFactory beanFactory;
   private volatile String @Nullable [] cachedAdvisorBeanNames;

   public BeanFactoryAdvisorRetrievalHelper(ConfigurableListableBeanFactory beanFactory) {
      Assert.notNull(beanFactory, "ListableBeanFactory must not be null");
      this.beanFactory = beanFactory;
   }

   public List<Advisor> findAdvisorBeans() {
      String[] advisorNames = this.cachedAdvisorBeanNames;
      if (advisorNames == null) {
         advisorNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(this.beanFactory, Advisor.class, true, false);
         this.cachedAdvisorBeanNames = advisorNames;
      }

      if (advisorNames.length == 0) {
         return new ArrayList<>();
      }

      List<Advisor> advisors = new ArrayList<>();

      for (String name : advisorNames) {
         if (this.isEligibleBean(name)) {
            if (this.beanFactory.isCurrentlyInCreation(name)) {
               if (logger.isTraceEnabled()) {
                  logger.trace("Skipping currently created advisor '" + name + "'");
               }
            } else {
               try {
                  advisors.add((Advisor)this.beanFactory.getBean(name, Advisor.class));
               } catch (BeanCreationException ex) {
                  if (ex.getMostSpecificCause() instanceof BeanCurrentlyInCreationException bce) {
                     String bceBeanName = bce.getBeanName();
                     if (bceBeanName != null && this.beanFactory.isCurrentlyInCreation(bceBeanName)) {
                        if (logger.isTraceEnabled()) {
                           logger.trace("Skipping advisor '" + name + "' with dependency on currently created bean: " + ex.getMessage());
                        }
                        continue;
                     }
                  }

                  throw ex;
               }
            }
         }
      }

      return advisors;
   }

   protected boolean isEligibleBean(String beanName) {
      return true;
   }
}
