package org.springframework.boot.support;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.PropertySourceInfo;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginLookup;
import org.springframework.boot.origin.SystemEnvironmentOrigin;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.util.StringUtils;

public class SystemEnvironmentPropertySourceEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
   public static final int DEFAULT_ORDER = -2147483644;
   private int order = -2147483644;

   @Override
   public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
      this.postProcessEnvironment(environment, application.getEnvironmentPrefix());
   }

   private void postProcessEnvironment(ConfigurableEnvironment environment, @Nullable String environmentPrefix) {
      String sourceName = "systemEnvironment";
      PropertySource<?> propertySource = environment.getPropertySources().get(sourceName);
      if (propertySource != null) {
         this.replacePropertySource(environment, sourceName, propertySource, environmentPrefix);
      }
   }

   private void replacePropertySource(
      ConfigurableEnvironment environment, String sourceName, PropertySource<?> propertySource, @Nullable String environmentPrefix
   ) {
      Map<String, Object> originalSource = (Map<String, Object>)propertySource.getSource();
      SystemEnvironmentPropertySource source = new SystemEnvironmentPropertySourceEnvironmentPostProcessor.OriginAwareSystemEnvironmentPropertySource(
         sourceName, originalSource, environmentPrefix
      );
      environment.getPropertySources().replace(sourceName, source);
   }

   public int getOrder() {
      return this.order;
   }

   public void setOrder(int order) {
      this.order = order;
   }

   public static void postProcessEnvironment(ConfigurableEnvironment environment, ConfigurableEnvironment parentEnvironment) {
      if (parentEnvironment.getPropertySources().get("systemEnvironment") instanceof SystemEnvironmentPropertySourceEnvironmentPostProcessor.OriginAwareSystemEnvironmentPropertySource parentOriginAwareSystemEnvironmentPropertySource
         )
       {
         new SystemEnvironmentPropertySourceEnvironmentPostProcessor()
            .postProcessEnvironment(environment, parentOriginAwareSystemEnvironmentPropertySource.getPrefix());
      }
   }

   protected static class OriginAwareSystemEnvironmentPropertySource
      extends SystemEnvironmentPropertySource
      implements PropertySourceInfo,
      OriginLookup<String> {
      private final @Nullable String prefix;

      OriginAwareSystemEnvironmentPropertySource(String name, Map<String, Object> source, @Nullable String environmentPrefix) {
         super(name, source);
         this.prefix = this.determinePrefix(environmentPrefix);
      }

      private @Nullable String determinePrefix(@Nullable String environmentPrefix) {
         if (!StringUtils.hasText(environmentPrefix)) {
            return null;
         } else {
            return !environmentPrefix.endsWith(".") && !environmentPrefix.endsWith("_") && !environmentPrefix.endsWith("-")
               ? environmentPrefix
               : environmentPrefix.substring(0, environmentPrefix.length() - 1);
         }
      }

      public boolean containsProperty(String name) {
         return super.containsProperty(name);
      }

      public @Nullable Object getProperty(String name) {
         return super.getProperty(name);
      }

      public @Nullable Origin getOrigin(String key) {
         String property = this.resolvePropertyName(key);
         return super.containsProperty(property) ? new SystemEnvironmentOrigin(property) : null;
      }

      @Override
      public @Nullable String getPrefix() {
         return this.prefix;
      }

      @Override
      public boolean isImmutable() {
         return this.getSource() == System.getenv();
      }
   }
}
