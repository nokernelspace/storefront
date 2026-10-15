package org.springframework.beans.factory.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;

public class YamlMapFactoryBean extends YamlProcessor implements FactoryBean<Map<String, Object>>, InitializingBean {
   private boolean singleton = true;
   private @Nullable Map<String, Object> map;

   public void setSingleton(boolean singleton) {
      this.singleton = singleton;
   }

   @Override
   public boolean isSingleton() {
      return this.singleton;
   }

   @Override
   public void afterPropertiesSet() {
      if (this.isSingleton()) {
         this.map = this.createMap();
      }
   }

   public @Nullable Map<String, Object> getObject() {
      return this.map != null ? this.map : this.createMap();
   }

   @Override
   public Class<?> getObjectType() {
      return Map.class;
   }

   protected Map<String, Object> createMap() {
      Map<String, Object> result = new LinkedHashMap<>();
      this.process((properties, map) -> this.merge(result, map));
      return result;
   }

   private void merge(Map<String, Object> output, Map<String, Object> map) {
      map.forEach((key, value) -> {
         Object existing = output.get(key);
         if (value instanceof Map valueMap && existing instanceof Map existingMap) {
            Map<String, Object> result = new LinkedHashMap<>(existingMap);
            this.merge(result, valueMap);
            output.put(key, result);
         } else {
            output.put(key, value);
         }
      });
   }
}
