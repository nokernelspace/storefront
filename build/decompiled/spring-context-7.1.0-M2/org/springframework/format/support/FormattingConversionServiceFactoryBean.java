package org.springframework.format.support;

import java.lang.annotation.Annotation;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.core.convert.support.ConversionServiceFactory;
import org.springframework.format.AnnotationFormatterFactory;
import org.springframework.format.Formatter;
import org.springframework.format.FormatterRegistrar;
import org.springframework.util.StringValueResolver;

public class FormattingConversionServiceFactoryBean implements FactoryBean<FormattingConversionService>, EmbeddedValueResolverAware, InitializingBean {
   private @Nullable Set<?> converters;
   private @Nullable Set<?> formatters;
   private @Nullable Set<FormatterRegistrar> formatterRegistrars;
   private boolean registerDefaultFormatters = true;
   private @Nullable StringValueResolver embeddedValueResolver;
   private @Nullable FormattingConversionService conversionService;

   public void setConverters(Set<?> converters) {
      this.converters = converters;
   }

   public void setFormatters(Set<?> formatters) {
      this.formatters = formatters;
   }

   public void setFormatterRegistrars(Set<FormatterRegistrar> formatterRegistrars) {
      this.formatterRegistrars = formatterRegistrars;
   }

   public void setRegisterDefaultFormatters(boolean registerDefaultFormatters) {
      this.registerDefaultFormatters = registerDefaultFormatters;
   }

   @Override
   public void setEmbeddedValueResolver(StringValueResolver embeddedValueResolver) {
      this.embeddedValueResolver = embeddedValueResolver;
   }

   public void afterPropertiesSet() {
      this.conversionService = new DefaultFormattingConversionService(this.embeddedValueResolver, this.registerDefaultFormatters);
      ConversionServiceFactory.registerConverters(this.converters, this.conversionService);
      this.registerFormatters(this.conversionService);
   }

   private void registerFormatters(FormattingConversionService conversionService) {
      if (this.formatters != null) {
         for (Object candidate : this.formatters) {
            if (candidate instanceof Formatter<?> formatter) {
               conversionService.addFormatter(formatter);
            } else {
               if (!(candidate instanceof AnnotationFormatterFactory<?> factory)) {
                  throw new IllegalArgumentException("Custom formatters must be implementations of Formatter or AnnotationFormatterFactory");
               }

               conversionService.addFormatterForFieldAnnotation((AnnotationFormatterFactory<? extends Annotation>)factory);
            }
         }
      }

      if (this.formatterRegistrars != null) {
         for (FormatterRegistrar registrar : this.formatterRegistrars) {
            registrar.registerFormatters(conversionService);
         }
      }
   }

   public @Nullable FormattingConversionService getObject() {
      return this.conversionService;
   }

   public Class<? extends FormattingConversionService> getObjectType() {
      return FormattingConversionService.class;
   }

   public boolean isSingleton() {
      return true;
   }
}
