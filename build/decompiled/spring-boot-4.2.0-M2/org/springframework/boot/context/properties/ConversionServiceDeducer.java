package org.springframework.boot.context.properties;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.format.support.FormattingConversionService;

class ConversionServiceDeducer {
   private final ApplicationContext applicationContext;

   ConversionServiceDeducer(ApplicationContext applicationContext) {
      this.applicationContext = applicationContext;
   }

   @Nullable List<ConversionService> getConversionServices() {
      if (this.hasUserDefinedConfigurationServiceBean()) {
         return Collections.singletonList((ConversionService)this.applicationContext.getBean("conversionService", ConversionService.class));
      } else {
         return this.applicationContext instanceof ConfigurableApplicationContext configurableContext ? this.getConversionServices(configurableContext) : null;
      }
   }

   private List<ConversionService> getConversionServices(ConfigurableApplicationContext applicationContext) {
      List<ConversionService> conversionServices = new ArrayList<>();
      FormattingConversionService beansConverterService = new FormattingConversionService();
      Map<String, Object> converterBeans = this.addBeans(applicationContext, beansConverterService);
      if (!converterBeans.isEmpty()) {
         conversionServices.add(beansConverterService);
      }

      if (applicationContext.getBeanFactory().getConversionService() != null) {
         conversionServices.add(applicationContext.getBeanFactory().getConversionService());
      }

      if (!converterBeans.isEmpty()) {
         conversionServices.add(ApplicationConversionService.getSharedInstance());
      }

      return conversionServices;
   }

   private Map<String, Object> addBeans(ConfigurableApplicationContext applicationContext, FormattingConversionService converterService) {
      DefaultConversionService.addCollectionConverters(converterService);
      converterService.addConverter(new ConfigurationPropertiesCharSequenceToObjectConverter(converterService));
      return ApplicationConversionService.addBeans(
         converterService, applicationContext.getBeanFactory(), "org.springframework.boot.context.properties.ConfigurationPropertiesBinding"
      );
   }

   private boolean hasUserDefinedConfigurationServiceBean() {
      String beanName = "conversionService";
      return this.applicationContext.containsBean(beanName)
         && this.applicationContext.getAutowireCapableBeanFactory().isTypeMatch(beanName, ConversionService.class);
   }
}
