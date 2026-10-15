package org.springframework.beans.factory.wiring;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

public class BeanConfigurerSupport implements BeanFactoryAware, InitializingBean, DisposableBean {
   protected final Log logger = LogFactory.getLog(this.getClass());
   private volatile @Nullable BeanWiringInfoResolver beanWiringInfoResolver;
   private volatile @Nullable ConfigurableListableBeanFactory beanFactory;

   public void setBeanWiringInfoResolver(BeanWiringInfoResolver beanWiringInfoResolver) {
      Assert.notNull(beanWiringInfoResolver, "BeanWiringInfoResolver must not be null");
      this.beanWiringInfoResolver = beanWiringInfoResolver;
   }

   @Override
   public void setBeanFactory(BeanFactory beanFactory) {
      if (beanFactory instanceof ConfigurableListableBeanFactory clbf) {
         this.beanFactory = clbf;
         if (this.beanWiringInfoResolver == null) {
            this.beanWiringInfoResolver = this.createDefaultBeanWiringInfoResolver();
         }
      } else {
         throw new IllegalArgumentException("Bean configurer aspect needs to run in a ConfigurableListableBeanFactory: " + beanFactory);
      }
   }

   protected @Nullable BeanWiringInfoResolver createDefaultBeanWiringInfoResolver() {
      return new ClassNameBeanWiringInfoResolver();
   }

   @Override
   public void afterPropertiesSet() {
      Assert.notNull(this.beanFactory, "BeanFactory must be set");
   }

   @Override
   public void destroy() {
      this.beanFactory = null;
      this.beanWiringInfoResolver = null;
   }

   public void configureBean(Object beanInstance) {
      if (this.beanFactory == null) {
         if (this.logger.isDebugEnabled()) {
            this.logger
               .debug(
                  "BeanFactory has not been set on "
                     + ClassUtils.getShortName(this.getClass())
                     + ": Make sure this configurer runs in a Spring container. Unable to configure bean of type ["
                     + ClassUtils.getDescriptiveType(beanInstance)
                     + "]. Proceeding without injection."
               );
         }
      } else {
         BeanWiringInfoResolver bwiResolver = this.beanWiringInfoResolver;
         Assert.state(bwiResolver != null, "No BeanWiringInfoResolver available");
         BeanWiringInfo bwi = bwiResolver.resolveWiringInfo(beanInstance);
         if (bwi != null) {
            ConfigurableListableBeanFactory beanFactory = this.beanFactory;
            Assert.state(beanFactory != null, "No BeanFactory available");

            try {
               String beanName = bwi.getBeanName();
               if (bwi.indicatesAutowiring() || bwi.isDefaultBeanName() && beanName != null && !beanFactory.containsBean(beanName)) {
                  beanFactory.autowireBeanProperties(beanInstance, bwi.getAutowireMode(), bwi.getDependencyCheck());
                  beanFactory.initializeBean(beanInstance, beanName != null ? beanName : "");
               } else {
                  beanFactory.configureBean(beanInstance, beanName != null ? beanName : "");
               }
            } catch (BeanCreationException ex) {
               if (ex.getMostSpecificCause() instanceof BeanCurrentlyInCreationException bce) {
                  String bceBeanName = bce.getBeanName();
                  if (bceBeanName != null && beanFactory.isCurrentlyInCreation(bceBeanName)) {
                     if (this.logger.isDebugEnabled()) {
                        this.logger
                           .debug(
                              "Failed to create target bean '"
                                 + bce.getBeanName()
                                 + "' while configuring object of type ["
                                 + beanInstance.getClass().getName()
                                 + "] - probably due to a circular reference. This is a common startup situation and usually not fatal. Proceeding without injection. Original exception: "
                                 + ex
                           );
                     }

                     return;
                  }
               }

               throw ex;
            }
         }
      }
   }
}
