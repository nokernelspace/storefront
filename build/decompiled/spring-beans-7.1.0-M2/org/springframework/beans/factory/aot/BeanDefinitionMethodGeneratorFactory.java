package org.springframework.beans.factory.aot;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.core.log.LogMessage;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

class BeanDefinitionMethodGeneratorFactory {
   private static final Log logger = LogFactory.getLog(BeanDefinitionMethodGeneratorFactory.class);
   private final AotServices<BeanRegistrationAotProcessor> aotProcessors;
   private final AotServices<BeanRegistrationExcludeFilter> excludeFilters;

   BeanDefinitionMethodGeneratorFactory(ConfigurableListableBeanFactory beanFactory) {
      this(AotServices.factoriesAndBeans(beanFactory));
   }

   BeanDefinitionMethodGeneratorFactory(AotServices.Loader loader) {
      this.aotProcessors = loader.load(BeanRegistrationAotProcessor.class);
      this.excludeFilters = loader.load(BeanRegistrationExcludeFilter.class);

      for (BeanRegistrationExcludeFilter excludeFilter : this.excludeFilters) {
         if (this.excludeFilters.getSource(excludeFilter) == AotServices.Source.BEAN_FACTORY) {
            Assert.state(
               excludeFilter instanceof BeanRegistrationAotProcessor || excludeFilter instanceof BeanFactoryInitializationAotProcessor,
               () -> "BeanRegistrationExcludeFilter bean of type %s must also implement an AOT processor interface"
                  .formatted(excludeFilter.getClass().getName())
            );
         }
      }
   }

   @Nullable BeanDefinitionMethodGenerator getBeanDefinitionMethodGenerator(RegisteredBean registeredBean, @Nullable String currentPropertyName) {
      if (this.isExcluded(registeredBean)) {
         return null;
      }

      List<BeanRegistrationAotContribution> contributions = this.getAotContributions(registeredBean);
      return new BeanDefinitionMethodGenerator(this, registeredBean, currentPropertyName, contributions);
   }

   @Nullable BeanDefinitionMethodGenerator getBeanDefinitionMethodGenerator(RegisteredBean registeredBean) {
      return this.getBeanDefinitionMethodGenerator(registeredBean, null);
   }

   private boolean isExcluded(RegisteredBean registeredBean) {
      if (this.isImplicitlyExcluded(registeredBean)) {
         return true;
      }

      for (BeanRegistrationExcludeFilter excludeFilter : this.excludeFilters) {
         if (excludeFilter.isExcludedFromAotProcessing(registeredBean)) {
            logger.trace(
               LogMessage.format(
                  "Excluding registered bean '%s' from bean factory %s due to %s",
                  registeredBean.getBeanName(),
                  ObjectUtils.identityToString(registeredBean.getBeanFactory()),
                  excludeFilter.getClass().getName()
               )
            );
            return true;
         }
      }

      return false;
   }

   private boolean isImplicitlyExcluded(RegisteredBean registeredBean) {
      if (Boolean.TRUE.equals(registeredBean.getMergedBeanDefinition().getAttribute("aotProcessingIgnoreRegistration"))) {
         return true;
      }

      Class<?> beanClass = registeredBean.getBeanClass();
      if (BeanFactoryInitializationAotProcessor.class.isAssignableFrom(beanClass)) {
         return true;
      }

      if (!BeanRegistrationAotProcessor.class.isAssignableFrom(beanClass)) {
         return false;
      }

      BeanRegistrationAotProcessor processor = this.aotProcessors.findByBeanName(registeredBean.getBeanName());
      return processor == null || processor.isBeanExcludedFromAotProcessing();
   }

   private List<BeanRegistrationAotContribution> getAotContributions(RegisteredBean registeredBean) {
      String beanName = registeredBean.getBeanName();
      List<BeanRegistrationAotContribution> contributions = new ArrayList<>();

      for (BeanRegistrationAotProcessor aotProcessor : this.aotProcessors) {
         BeanRegistrationAotContribution contribution = aotProcessor.processAheadOfTime(registeredBean);
         if (contribution != null) {
            logger.trace(
               LogMessage.format(
                  "Adding bean registration AOT contribution %S from %S to '%S'",
                  contribution.getClass().getName(),
                  aotProcessor.getClass().getName(),
                  beanName
               )
            );
            contributions.add(contribution);
         }
      }

      return contributions;
   }
}
