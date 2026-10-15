package org.springframework.beans.factory.aot;

import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.RegisteredBean;

class BeanRegistrationsAotProcessor implements BeanFactoryInitializationAotProcessor {
   public @Nullable BeanRegistrationsAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
      BeanDefinitionMethodGeneratorFactory beanDefinitionMethodGeneratorFactory = new BeanDefinitionMethodGeneratorFactory(beanFactory);
      List<BeanRegistrationsAotContribution.Registration> registrations = new ArrayList<>();

      for (String beanName : beanFactory.getBeanDefinitionNames()) {
         RegisteredBean registeredBean = RegisteredBean.of(beanFactory, beanName);
         BeanDefinitionMethodGenerator beanDefinitionMethodGenerator = beanDefinitionMethodGeneratorFactory.getBeanDefinitionMethodGenerator(registeredBean);
         if (beanDefinitionMethodGenerator != null) {
            registrations.add(
               new BeanRegistrationsAotContribution.Registration(registeredBean, beanDefinitionMethodGenerator, beanFactory.getAliases(beanName))
            );
         }
      }

      return registrations.isEmpty() ? null : new BeanRegistrationsAotContribution(registrations);
   }
}
