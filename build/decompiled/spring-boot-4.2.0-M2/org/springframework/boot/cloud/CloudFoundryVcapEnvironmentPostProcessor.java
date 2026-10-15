package org.springframework.boot.cloud;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.util.StringUtils;

public class CloudFoundryVcapEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {
   private final Log logger;
   private int order = -2147483643;

   public CloudFoundryVcapEnvironmentPostProcessor(DeferredLogFactory logFactory) {
      this.logger = logFactory.getLog(CloudFoundryVcapEnvironmentPostProcessor.class);
   }

   public void setOrder(int order) {
      this.order = order;
   }

   public int getOrder() {
      return this.order;
   }

   @Override
   public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
      if (CloudPlatform.CLOUD_FOUNDRY.isActive(environment)) {
         Properties properties = new Properties();
         JsonParser jsonParser = JsonParserFactory.getJsonParser();
         this.addWithPrefix(properties, this.getPropertiesFromApplication(environment, jsonParser), "vcap.application.");
         this.addWithPrefix(properties, this.getPropertiesFromServices(environment, jsonParser), "vcap.services.");
         MutablePropertySources propertySources = environment.getPropertySources();
         PropertiesPropertySource vcapSource = new PropertiesPropertySource("vcap", properties);
         if (propertySources.contains("commandLineArgs")) {
            propertySources.addAfter("commandLineArgs", vcapSource);
         } else {
            propertySources.addFirst(vcapSource);
         }
      }
   }

   private void addWithPrefix(Properties properties, Properties other, String prefix) {
      for (String key : other.stringPropertyNames()) {
         String prefixed = prefix + key;
         properties.setProperty(prefixed, other.getProperty(key));
      }
   }

   private Properties getPropertiesFromApplication(Environment environment, JsonParser parser) {
      Properties properties = new Properties();

      try {
         String property = environment.getProperty("VCAP_APPLICATION", "{}");
         Map<String, Object> map = parser.parseMap(property);
         this.extractPropertiesFromApplication(properties, map);
      } catch (Exception ex) {
         this.logger.error("Could not parse VCAP_APPLICATION", ex);
      }

      return properties;
   }

   private Properties getPropertiesFromServices(Environment environment, JsonParser parser) {
      Properties properties = new Properties();

      try {
         String property = environment.getProperty("VCAP_SERVICES", "{}");
         Map<String, Object> map = parser.parseMap(property);
         this.extractPropertiesFromServices(properties, map);
      } catch (Exception ex) {
         this.logger.error("Could not parse VCAP_SERVICES", ex);
      }

      return properties;
   }

   private void extractPropertiesFromApplication(Properties properties, @Nullable Map<String, Object> map) {
      if (map != null) {
         this.flatten(properties, map, "");
      }
   }

   private void extractPropertiesFromServices(Properties properties, @Nullable Map<String, Object> map) {
      if (map != null) {
         for (Object services : map.values()) {
            for (Object object : (List)services) {
               Map<String, Object> service = (Map<String, Object>)object;
               String key = (String)service.get("name");
               if (key == null) {
                  key = (String)service.get("label");
               }

               this.flatten(properties, service, key);
            }
         }
      }
   }

   private void flatten(Properties properties, Map<String, Object> input, @Nullable String path) {
      input.forEach((key, value) -> {
         String name = this.getPropertyName(path, key);
         if (value instanceof Map) {
            this.flatten(properties, (Map<String, Object>)value, name);
         } else if (value instanceof Collection<?> collection) {
            properties.put(name, StringUtils.collectionToCommaDelimitedString(collection));
            int count = 0;

            for (Object item : collection) {
               String itemKey = "[" + count++ + "]";
               this.flatten(properties, Collections.singletonMap(itemKey, item), name);
            }
         } else if (value instanceof String) {
            properties.put(name, value);
         } else if (!(value instanceof Number) && !(value instanceof Boolean)) {
            properties.put(name, value != null ? value : "");
         } else {
            properties.put(name, value.toString());
         }
      });
   }

   private String getPropertyName(@Nullable String path, String key) {
      if (!StringUtils.hasText(path)) {
         return key;
      } else {
         return key.startsWith("[") ? path + key : path + "." + key;
      }
   }
}
