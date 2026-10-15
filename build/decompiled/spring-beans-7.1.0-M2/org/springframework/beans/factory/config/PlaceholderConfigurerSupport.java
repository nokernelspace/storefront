package org.springframework.beans.factory.config;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.core.env.AbstractPropertyResolver;
import org.springframework.util.StringValueResolver;
import org.springframework.util.SystemPropertyUtils;

public abstract class PlaceholderConfigurerSupport extends PropertyResourceConfigurer implements BeanNameAware, BeanFactoryAware {
   public static final String DEFAULT_PLACEHOLDER_PREFIX = "${";
   public static final String DEFAULT_PLACEHOLDER_SUFFIX = "}";
   public static final String DEFAULT_VALUE_SEPARATOR = ":";
   public static final Character DEFAULT_ESCAPE_CHARACTER = SystemPropertyUtils.ESCAPE_CHARACTER;
   protected String placeholderPrefix = "${";
   protected String placeholderSuffix = "}";
   protected @Nullable String valueSeparator = ":";
   protected @Nullable Character escapeCharacter = AbstractPropertyResolver.getDefaultEscapeCharacter();
   protected boolean trimValues = false;
   protected @Nullable String nullValue;
   protected boolean ignoreUnresolvablePlaceholders = false;
   private @Nullable String beanName;
   private @Nullable BeanFactory beanFactory;

   public void setPlaceholderPrefix(String placeholderPrefix) {
      this.placeholderPrefix = placeholderPrefix;
   }

   public void setPlaceholderSuffix(String placeholderSuffix) {
      this.placeholderSuffix = placeholderSuffix;
   }

   public void setValueSeparator(@Nullable String valueSeparator) {
      this.valueSeparator = valueSeparator;
   }

   public void setEscapeCharacter(@Nullable Character escapeCharacter) {
      this.escapeCharacter = escapeCharacter;
   }

   public void setTrimValues(boolean trimValues) {
      this.trimValues = trimValues;
   }

   public void setNullValue(String nullValue) {
      this.nullValue = nullValue;
   }

   public void setIgnoreUnresolvablePlaceholders(boolean ignoreUnresolvablePlaceholders) {
      this.ignoreUnresolvablePlaceholders = ignoreUnresolvablePlaceholders;
   }

   @Override
   public void setBeanName(String beanName) {
      this.beanName = beanName;
   }

   @Override
   public void setBeanFactory(BeanFactory beanFactory) {
      this.beanFactory = beanFactory;
   }

   protected void doProcessProperties(ConfigurableListableBeanFactory beanFactoryToProcess, StringValueResolver valueResolver) {
      BeanDefinitionVisitor visitor = new BeanDefinitionVisitor(valueResolver);
      String[] beanNames = beanFactoryToProcess.getBeanDefinitionNames();

      for (String curName : beanNames) {
         if (!curName.equals(this.beanName) || !beanFactoryToProcess.equals(this.beanFactory)) {
            BeanDefinition bd = beanFactoryToProcess.getBeanDefinition(curName);

            try {
               visitor.visitBeanDefinition(bd);
            } catch (Exception ex) {
               throw new BeanDefinitionStoreException(bd.getResourceDescription(), curName, ex.getMessage(), ex);
            }
         }
      }

      beanFactoryToProcess.resolveAliases(valueResolver);
      beanFactoryToProcess.addEmbeddedValueResolver(valueResolver);
   }
}
