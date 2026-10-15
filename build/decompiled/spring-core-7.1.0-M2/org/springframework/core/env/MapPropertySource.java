package org.springframework.core.env;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

public class MapPropertySource extends EnumerablePropertySource<Map<String, Object>> {
   public MapPropertySource(String name, Map<String, Object> source) {
      super(name, source);
   }

   @Override
   public @Nullable Object getProperty(String name) {
      return this.source.get(name);
   }

   @Override
   public boolean containsProperty(String name) {
      return this.source.containsKey(name);
   }

   @Override
   public String[] getPropertyNames() {
      return StringUtils.toStringArray(this.source.keySet());
   }
}
