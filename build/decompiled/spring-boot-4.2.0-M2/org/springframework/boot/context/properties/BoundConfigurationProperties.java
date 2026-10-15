package org.springframework.boot.context.properties;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.context.properties.source.ConfigurationProperty;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.context.ApplicationContext;
import org.springframework.util.Assert;

public class BoundConfigurationProperties {
   private final Map<ConfigurationPropertyName, ConfigurationProperty> properties = new LinkedHashMap<>();
   private static final String BEAN_NAME = BoundConfigurationProperties.class.getName();

   void add(ConfigurationProperty configurationProperty) {
      this.properties.put(configurationProperty.getName(), configurationProperty);
   }

   public @Nullable ConfigurationProperty get(ConfigurationPropertyName name) {
      return this.properties.get(name);
   }

   public Map<ConfigurationPropertyName, ConfigurationProperty> getAll() {
      return Collections.unmodifiableMap(this.properties);
   }

   public static @Nullable BoundConfigurationProperties get(ApplicationContext context) {
      return !context.containsBeanDefinition(BEAN_NAME) ? null : (BoundConfigurationProperties)context.getBean(BEAN_NAME, BoundConfigurationProperties.class);
   }

   static void register(BeanDefinitionRegistry registry) {
      Assert.notNull(registry, "'registry' must not be null");
      if (!registry.containsBeanDefinition(BEAN_NAME)) {
         BeanDefinition definition = BeanDefinitionBuilder.rootBeanDefinition(BoundConfigurationProperties.class).setRole(2).getBeanDefinition();
         registry.registerBeanDefinition(BEAN_NAME, definition);
      }
   }
}
