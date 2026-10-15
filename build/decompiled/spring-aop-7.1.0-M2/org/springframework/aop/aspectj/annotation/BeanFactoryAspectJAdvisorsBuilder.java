package org.springframework.aop.aspectj.annotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.reflect.PerClauseKind;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.Advisor;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.util.Assert;

public class BeanFactoryAspectJAdvisorsBuilder {
   private static final Log logger = LogFactory.getLog(BeanFactoryAspectJAdvisorsBuilder.class);
   private final ListableBeanFactory beanFactory;
   private final AspectJAdvisorFactory advisorFactory;
   private volatile @Nullable List<String> aspectBeanNames;
   private final Map<String, List<Advisor>> advisorsCache = new ConcurrentHashMap<>();
   private final Map<String, MetadataAwareAspectInstanceFactory> aspectFactoryCache = new ConcurrentHashMap<>();

   public BeanFactoryAspectJAdvisorsBuilder(ListableBeanFactory beanFactory) {
      this(beanFactory, new ReflectiveAspectJAdvisorFactory(beanFactory));
   }

   public BeanFactoryAspectJAdvisorsBuilder(ListableBeanFactory beanFactory, AspectJAdvisorFactory advisorFactory) {
      Assert.notNull(beanFactory, "ListableBeanFactory must not be null");
      Assert.notNull(advisorFactory, "AspectJAdvisorFactory must not be null");
      this.beanFactory = beanFactory;
      this.advisorFactory = advisorFactory;
   }

   public List<Advisor> buildAspectJAdvisors() {
      List<String> aspectNames = this.aspectBeanNames;
      if (aspectNames == null) {
         synchronized (this) {
            aspectNames = this.aspectBeanNames;
            if (aspectNames == null) {
               List<Advisor> advisors = new ArrayList<>();
               aspectNames = new ArrayList<>();
               String[] beanNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(this.beanFactory, Object.class, true, false);

               for (String beanName : beanNames) {
                  if (this.isEligibleBean(beanName)) {
                     Class<?> beanType = this.beanFactory.getType(beanName, false);
                     if (beanType != null && this.advisorFactory.isAspect(beanType)) {
                        try {
                           AspectMetadata amd = new AspectMetadata(beanType, beanName);
                           if (amd.getAjType().getPerClause().getKind() == PerClauseKind.SINGLETON) {
                              MetadataAwareAspectInstanceFactory factory = new BeanFactoryAspectInstanceFactory(this.beanFactory, beanName);
                              List<Advisor> classAdvisors = this.advisorFactory.getAdvisors(factory);
                              if (this.beanFactory.isSingleton(beanName)) {
                                 this.advisorsCache.put(beanName, classAdvisors);
                              } else {
                                 this.aspectFactoryCache.put(beanName, factory);
                              }

                              advisors.addAll(classAdvisors);
                           } else {
                              if (this.beanFactory.isSingleton(beanName)) {
                                 throw new IllegalArgumentException(
                                    "Bean with name '" + beanName + "' is a singleton, but aspect instantiation model is not singleton"
                                 );
                              }

                              MetadataAwareAspectInstanceFactory factory = new PrototypeAspectInstanceFactory(this.beanFactory, beanName);
                              this.aspectFactoryCache.put(beanName, factory);
                              advisors.addAll(this.advisorFactory.getAdvisors(factory));
                           }

                           aspectNames.add(beanName);
                        } catch (IllegalArgumentException | IllegalStateException | AopConfigException ex) {
                           if (logger.isDebugEnabled()) {
                              logger.debug("Ignoring incompatible aspect [" + beanType.getName() + "]: " + ex);
                           }
                        }
                     }
                  }
               }

               this.aspectBeanNames = aspectNames;
               return advisors;
            }
         }
      }

      if (aspectNames.isEmpty()) {
         return Collections.emptyList();
      }

      List<Advisor> advisors = new ArrayList<>();

      for (String aspectName : aspectNames) {
         List<Advisor> cachedAdvisors = this.advisorsCache.get(aspectName);
         if (cachedAdvisors != null) {
            advisors.addAll(cachedAdvisors);
         } else {
            MetadataAwareAspectInstanceFactory factory = this.aspectFactoryCache.get(aspectName);
            Assert.state(factory != null, "Factory must not be null");
            advisors.addAll(this.advisorFactory.getAdvisors(factory));
         }
      }

      return advisors;
   }

   protected boolean isEligibleBean(String beanName) {
      return true;
   }
}
