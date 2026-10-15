package org.springframework.beans.factory.config;

import java.util.Map;
import java.util.Properties;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.core.SpringProperties;
import org.springframework.util.Assert;
import org.springframework.util.PropertyPlaceholderHelper;
import org.springframework.util.StringValueResolver;
import org.springframework.util.PropertyPlaceholderHelper.PlaceholderResolver;

@Deprecated(since = "5.2", forRemoval = true)
public class PropertyPlaceholderConfigurer extends PlaceholderConfigurerSupport {
   public static final int SYSTEM_PROPERTIES_MODE_NEVER = 0;
   public static final int SYSTEM_PROPERTIES_MODE_FALLBACK = 1;
   public static final int SYSTEM_PROPERTIES_MODE_OVERRIDE = 2;
   private static final Map<String, Integer> constants = Map.of(
      "SYSTEM_PROPERTIES_MODE_NEVER", 0, "SYSTEM_PROPERTIES_MODE_FALLBACK", 1, "SYSTEM_PROPERTIES_MODE_OVERRIDE", 2
   );
   private int systemPropertiesMode = 1;
   private boolean searchSystemEnvironment = !SpringProperties.getFlag("spring.getenv.ignore");

   public void setSystemPropertiesModeName(String constantName) throws IllegalArgumentException {
      Assert.hasText(constantName, "'constantName' must not be null or blank");
      Integer mode = constants.get(constantName);
      Assert.notNull(mode, "Only system properties mode constants allowed");
      this.systemPropertiesMode = mode;
   }

   public void setSystemPropertiesMode(int systemPropertiesMode) {
      this.systemPropertiesMode = systemPropertiesMode;
   }

   public void setSearchSystemEnvironment(boolean searchSystemEnvironment) {
      this.searchSystemEnvironment = searchSystemEnvironment;
   }

   protected @Nullable String resolvePlaceholder(String placeholder, Properties props, int systemPropertiesMode) {
      String propVal = null;
      if (systemPropertiesMode == 2) {
         propVal = this.resolveSystemProperty(placeholder);
      }

      if (propVal == null) {
         propVal = this.resolvePlaceholder(placeholder, props);
      }

      if (propVal == null && systemPropertiesMode == 1) {
         propVal = this.resolveSystemProperty(placeholder);
      }

      return propVal;
   }

   protected @Nullable String resolvePlaceholder(String placeholder, Properties props) {
      return props.getProperty(placeholder);
   }

   protected @Nullable String resolveSystemProperty(String key) {
      try {
         String value = System.getProperty(key);
         if (value == null && this.searchSystemEnvironment) {
            value = System.getenv(key);
         }

         return value;
      } catch (Throwable ex) {
         if (this.logger.isDebugEnabled()) {
            this.logger.debug("Could not access system property '" + key + "': " + ex);
         }

         return null;
      }
   }

   @Override
   protected void processProperties(ConfigurableListableBeanFactory beanFactoryToProcess, Properties props) throws BeansException {
      StringValueResolver valueResolver = new PropertyPlaceholderConfigurer.PlaceholderResolvingStringValueResolver(props);
      this.doProcessProperties(beanFactoryToProcess, valueResolver);
   }

   private class PlaceholderResolvingStringValueResolver implements StringValueResolver {
      private final PropertyPlaceholderHelper helper = new PropertyPlaceholderHelper(
         PropertyPlaceholderConfigurer.this.placeholderPrefix,
         PropertyPlaceholderConfigurer.this.placeholderSuffix,
         PropertyPlaceholderConfigurer.this.valueSeparator,
         PropertyPlaceholderConfigurer.this.escapeCharacter,
         PropertyPlaceholderConfigurer.this.ignoreUnresolvablePlaceholders
      );
      private final PlaceholderResolver resolver;

      public PlaceholderResolvingStringValueResolver(Properties props) {
         this.resolver = PropertyPlaceholderConfigurer.this.new PropertyPlaceholderConfigurerResolver(props);
      }

      public @Nullable String resolveStringValue(String strVal) throws BeansException {
         String resolved = this.helper.replacePlaceholders(strVal, this.resolver);
         if (PropertyPlaceholderConfigurer.this.trimValues) {
            resolved = resolved.trim();
         }

         return resolved.equals(PropertyPlaceholderConfigurer.this.nullValue) ? null : resolved;
      }
   }

   private final class PropertyPlaceholderConfigurerResolver implements PlaceholderResolver {
      private final Properties props;

      private PropertyPlaceholderConfigurerResolver(Properties props) {
         this.props = props;
      }

      public @Nullable String resolvePlaceholder(String placeholderName) {
         return PropertyPlaceholderConfigurer.this.resolvePlaceholder(placeholderName, this.props, PropertyPlaceholderConfigurer.this.systemPropertiesMode);
      }
   }
}
