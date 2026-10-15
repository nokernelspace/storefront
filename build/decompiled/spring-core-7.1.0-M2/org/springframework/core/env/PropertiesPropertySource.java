package org.springframework.core.env;

import java.util.Map;
import java.util.Properties;

public class PropertiesPropertySource extends MapPropertySource {
   public PropertiesPropertySource(String name, Properties source) {
      super(name, source);
   }

   protected PropertiesPropertySource(String name, Map<String, Object> source) {
      super(name, source);
   }

   @Override
   public String[] getPropertyNames() {
      synchronized ((Map)this.source) {
         return this.source.keySet().stream().filter(k -> k instanceof String).toArray(String[]::new);
      }
   }
}
