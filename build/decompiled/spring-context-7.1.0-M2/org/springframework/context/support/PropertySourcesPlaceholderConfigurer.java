package org.springframework.context.support;

import java.io.IOException;
import java.util.Properties;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.PlaceholderConfigurerSupport;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.ConfigurablePropertyResolver;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.util.Assert;
import org.springframework.util.StringValueResolver;

public class PropertySourcesPlaceholderConfigurer extends PlaceholderConfigurerSupport implements EnvironmentAware {
   public static final String LOCAL_PROPERTIES_PROPERTY_SOURCE_NAME = "localProperties";
   public static final String ENVIRONMENT_PROPERTIES_PROPERTY_SOURCE_NAME = "environmentProperties";
   private @Nullable MutablePropertySources propertySources;
   private @Nullable PropertySources appliedPropertySources;
   private @Nullable Environment environment;

   public void setPropertySources(PropertySources propertySources) {
      this.propertySources = new MutablePropertySources(propertySources);
   }

   @Override
   public void setEnvironment(Environment environment) {
      this.environment = environment;
   }

   public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
      if (this.propertySources == null) {
         this.propertySources = new MutablePropertySources();
         if (this.environment != null) {
            PropertySource<?> environmentPropertySource = (PropertySource<?>)(this.environment instanceof ConfigurableEnvironment configurableEnvironment
               ? new PropertySourcesPlaceholderConfigurer.ConfigurableEnvironmentPropertySource(configurableEnvironment)
               : new PropertySourcesPlaceholderConfigurer.FallbackEnvironmentPropertySource(this.environment));
            this.propertySources.addLast(environmentPropertySource);
         }

         try {
            PropertySource<?> localPropertySource = new PropertiesPropertySource("localProperties", this.mergeProperties());
            if (this.localOverride) {
               this.propertySources.addFirst(localPropertySource);
            } else {
               this.propertySources.addLast(localPropertySource);
            }
         } catch (IOException ex) {
            throw new BeanInitializationException("Could not load properties", ex);
         }
      }

      this.processProperties(beanFactory, this.createPropertyResolver(this.propertySources));
      this.appliedPropertySources = this.propertySources;
   }

   protected ConfigurablePropertyResolver createPropertyResolver(MutablePropertySources propertySources) {
      return new PropertySourcesPropertyResolver(propertySources);
   }

   protected void processProperties(ConfigurableListableBeanFactory beanFactoryToProcess, ConfigurablePropertyResolver propertyResolver) throws BeansException {
      propertyResolver.setPlaceholderPrefix(this.placeholderPrefix);
      propertyResolver.setPlaceholderSuffix(this.placeholderSuffix);
      propertyResolver.setValueSeparator(this.valueSeparator);
      propertyResolver.setEscapeCharacter(this.escapeCharacter);
      StringValueResolver valueResolver = strVal -> {
         String resolved = this.ignoreUnresolvablePlaceholders
            ? propertyResolver.resolvePlaceholders(strVal)
            : propertyResolver.resolveRequiredPlaceholders(strVal);
         if (this.trimValues) {
            resolved = resolved.trim();
         }

         return resolved.equals(this.nullValue) ? null : resolved;
      };
      this.doProcessProperties(beanFactoryToProcess, valueResolver);
   }

   @Deprecated(since = "3.1")
   protected void processProperties(ConfigurableListableBeanFactory beanFactory, Properties props) {
      throw new UnsupportedOperationException("Call processProperties(ConfigurableListableBeanFactory, ConfigurablePropertyResolver) instead");
   }

   public PropertySources getAppliedPropertySources() throws IllegalStateException {
      Assert.state(this.appliedPropertySources != null, "PropertySources have not yet been applied");
      return this.appliedPropertySources;
   }

   private static class ConfigurableEnvironmentPropertySource extends PropertySource<ConfigurableEnvironment> {
      ConfigurableEnvironmentPropertySource(ConfigurableEnvironment environment) {
         super("environmentProperties", environment);
      }

      public boolean containsProperty(String name) {
         for (PropertySource<?> propertySource : ((ConfigurableEnvironment)super.source).getPropertySources()) {
            if (propertySource.containsProperty(name)) {
               return true;
            }
         }

         return false;
      }

      public @Nullable String getProperty(String name) {
         for (PropertySource<?> propertySource : ((ConfigurableEnvironment)super.source).getPropertySources()) {
            Object candidate = propertySource.getProperty(name);
            if (candidate != null) {
               return this.convertToString(candidate);
            }
         }

         return null;
      }

      private @Nullable String convertToString(Object value) {
         return value instanceof String string ? string : (String)((ConfigurableEnvironment)super.source).getConversionService().convert(value, String.class);
      }

      public String toString() {
         return "ConfigurableEnvironmentPropertySource {propertySources=" + ((ConfigurableEnvironment)super.source).getPropertySources() + "}";
      }
   }

   private static class FallbackEnvironmentPropertySource extends PropertySource<Environment> {
      FallbackEnvironmentPropertySource(Environment environment) {
         super("environmentProperties", environment);
      }

      public boolean containsProperty(String name) {
         return ((Environment)super.source).containsProperty(name);
      }

      public @Nullable String getProperty(String name) {
         return ((Environment)super.source).getProperty(name);
      }

      public String toString() {
         return "FallbackEnvironmentPropertySource {environment=" + super.source + "}";
      }
   }
}
