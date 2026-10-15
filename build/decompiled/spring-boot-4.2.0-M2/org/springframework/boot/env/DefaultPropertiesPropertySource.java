package org.springframework.boot.env;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.util.CollectionUtils;

public class DefaultPropertiesPropertySource extends MapPropertySource {
   public static final String NAME = "defaultProperties";

   public DefaultPropertiesPropertySource(Map<String, Object> source) {
      super("defaultProperties", source);
   }

   public static boolean hasMatchingName(@Nullable PropertySource<?> propertySource) {
      return propertySource != null && propertySource.getName().equals("defaultProperties");
   }

   public static void ifNotEmpty(@Nullable Map<String, Object> source, @Nullable Consumer<DefaultPropertiesPropertySource> action) {
      if (!CollectionUtils.isEmpty(source) && action != null) {
         action.accept(new DefaultPropertiesPropertySource(source));
      }
   }

   public static void addOrMerge(Map<String, Object> source, MutablePropertySources sources) {
      if (!CollectionUtils.isEmpty(source)) {
         Map<String, Object> resultingSource = new HashMap<>();
         DefaultPropertiesPropertySource propertySource = new DefaultPropertiesPropertySource(resultingSource);
         if (sources.contains("defaultProperties")) {
            mergeIfPossible(source, sources, resultingSource);
            sources.replace("defaultProperties", propertySource);
         } else {
            resultingSource.putAll(source);
            sources.addLast(propertySource);
         }
      }
   }

   private static void mergeIfPossible(Map<String, Object> source, MutablePropertySources sources, Map<String, Object> resultingSource) {
      PropertySource<?> existingSource = sources.get("defaultProperties");
      if (existingSource != null) {
         Object underlyingSource = existingSource.getSource();
         if (underlyingSource instanceof Map) {
            resultingSource.putAll((Map<? extends String, ? extends Object>)underlyingSource);
         }

         resultingSource.putAll(source);
      }
   }

   public static void moveToEnd(ConfigurableEnvironment environment) {
      moveToEnd(environment.getPropertySources());
   }

   public static void moveToEnd(MutablePropertySources propertySources) {
      PropertySource<?> propertySource = propertySources.remove("defaultProperties");
      if (propertySource != null) {
         propertySources.addLast(propertySource);
      }
   }
}
