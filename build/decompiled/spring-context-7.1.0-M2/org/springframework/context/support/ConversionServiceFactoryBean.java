package org.springframework.context.support;

import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.ConversionServiceFactory;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.core.convert.support.GenericConversionService;

public class ConversionServiceFactoryBean implements FactoryBean<ConversionService>, InitializingBean {
   private @Nullable Set<?> converters;
   private @Nullable GenericConversionService conversionService;

   public void setConverters(Set<?> converters) {
      this.converters = converters;
   }

   public void afterPropertiesSet() {
      this.conversionService = this.createConversionService();
      ConversionServiceFactory.registerConverters(this.converters, this.conversionService);
   }

   protected GenericConversionService createConversionService() {
      return new DefaultConversionService();
   }

   public @Nullable ConversionService getObject() {
      return this.conversionService;
   }

   public Class<? extends ConversionService> getObjectType() {
      return GenericConversionService.class;
   }

   public boolean isSingleton() {
      return true;
   }
}
