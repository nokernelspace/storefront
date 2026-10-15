package org.springframework.boot.support;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.origin.OriginLookup;
import org.springframework.boot.origin.PropertySourceOrigin;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

public class SpringApplicationJsonEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
   public static final String SPRING_APPLICATION_JSON_PROPERTY = "spring.application.json";
   public static final String SPRING_APPLICATION_JSON_ENVIRONMENT_VARIABLE = "SPRING_APPLICATION_JSON";
   private static final String SERVLET_ENVIRONMENT_CLASS = "org.springframework.web.context.support.StandardServletEnvironment";
   private static final Set<String> SERVLET_ENVIRONMENT_PROPERTY_SOURCES = new LinkedHashSet<>(
      Arrays.asList("jndiProperties", "servletContextInitParams", "servletConfigInitParams")
   );
   public static final int DEFAULT_ORDER = -2147483643;
   private int order = -2147483643;

   public int getOrder() {
      return this.order;
   }

   public void setOrder(int order) {
      this.order = order;
   }

   @Override
   public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
      MutablePropertySources propertySources = environment.getPropertySources();
      propertySources.stream()
         .map(SpringApplicationJsonEnvironmentPostProcessor.JsonPropertyValue::get)
         .filter(Objects::nonNull)
         .findFirst()
         .ifPresent(v -> this.processJson(environment, v));
   }

   private void processJson(ConfigurableEnvironment environment, SpringApplicationJsonEnvironmentPostProcessor.JsonPropertyValue propertyValue) {
      JsonParser parser = JsonParserFactory.getJsonParser();
      Map<String, Object> map = parser.parseMap(propertyValue.getJson());
      if (!map.isEmpty()) {
         this.addJsonPropertySource(environment, new SpringApplicationJsonEnvironmentPostProcessor.JsonPropertySource(propertyValue, this.flatten(map)));
      }
   }

   private Map<String, Object> flatten(Map<String, Object> map) {
      Map<String, Object> result = new LinkedHashMap<>();
      this.flatten(null, result, map);
      return result;
   }

   private void flatten(@Nullable String prefix, Map<String, Object> result, Map<String, Object> map) {
      String namePrefix = prefix != null ? prefix + "." : "";
      map.forEach((key, value) -> this.extract(namePrefix + key, result, value));
   }

   private void extract(String name, Map<String, Object> result, @Nullable Object value) {
      if (value instanceof Map<?, ?> map) {
         if (CollectionUtils.isEmpty(map)) {
            result.put(name, "");
            return;
         }

         this.flatten(name, result, (Map<String, Object>)value);
      } else if (value instanceof Collection<?> collection) {
         if (CollectionUtils.isEmpty(collection)) {
            result.put(name, "");
            return;
         }

         int index = 0;

         for (Object object : collection) {
            this.extract(name + "[" + index + "]", result, object);
            index++;
         }
      } else {
         result.put(name, value != null ? value : "");
      }
   }

   private void addJsonPropertySource(ConfigurableEnvironment environment, PropertySource<?> source) {
      MutablePropertySources sources = environment.getPropertySources();
      String name = this.findPropertySource(sources);
      if (sources.contains(name)) {
         sources.addBefore(name, source);
      } else {
         sources.addFirst(source);
      }
   }

   private String findPropertySource(MutablePropertySources sources) {
      if (ClassUtils.isPresent("org.springframework.web.context.support.StandardServletEnvironment", null)) {
         PropertySource<?> servletPropertySource = sources.stream()
            .filter(source -> SERVLET_ENVIRONMENT_PROPERTY_SOURCES.contains(source.getName()))
            .findFirst()
            .orElse(null);
         if (servletPropertySource != null) {
            return servletPropertySource.getName();
         }
      }

      return "systemProperties";
   }

   private static class JsonPropertySource extends MapPropertySource implements OriginLookup<String> {
      private final SpringApplicationJsonEnvironmentPostProcessor.JsonPropertyValue propertyValue;

      JsonPropertySource(SpringApplicationJsonEnvironmentPostProcessor.JsonPropertyValue propertyValue, Map<String, Object> source) {
         super("spring.application.json", source);
         this.propertyValue = propertyValue;
      }

      public Origin getOrigin(String key) {
         return this.propertyValue.getOrigin();
      }
   }

   private static class JsonPropertyValue {
      private static final String[] CANDIDATES = new String[]{"spring.application.json", "SPRING_APPLICATION_JSON"};
      private final PropertySource<?> propertySource;
      private final String propertyName;
      private final String json;

      JsonPropertyValue(PropertySource<?> propertySource, String propertyName, String json) {
         this.propertySource = propertySource;
         this.propertyName = propertyName;
         this.json = json;
      }

      String getJson() {
         return this.json;
      }

      Origin getOrigin() {
         return PropertySourceOrigin.get(this.propertySource, this.propertyName);
      }

      static SpringApplicationJsonEnvironmentPostProcessor.@Nullable JsonPropertyValue get(PropertySource<?> propertySource) {
         for (String candidate : CANDIDATES) {
            if (propertySource.getProperty(candidate) instanceof String string && StringUtils.hasLength(string)) {
               return new SpringApplicationJsonEnvironmentPostProcessor.JsonPropertyValue(propertySource, candidate, string);
            }
         }

         return null;
      }
   }
}
