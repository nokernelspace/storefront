package org.springframework.boot.logging.log4j2;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import org.apache.logging.log4j.util.BiConsumer;
import org.apache.logging.log4j.util.PropertySource;
import org.apache.logging.log4j.util.PropertySource.Util;
import org.jspecify.annotations.Nullable;

public class SpringBootPropertySource implements PropertySource {
   private static final String PREFIX = "log4j.";
   private final Map<String, String> properties = Collections.singletonMap("log4j.shutdownHookEnabled", "false");

   public void forEach(BiConsumer<String, String> action) {
      this.properties.forEach(action);
   }

   public CharSequence getNormalForm(Iterable<? extends CharSequence> tokens) {
      return "log4j." + Util.joinAsCamelCase(tokens);
   }

   public int getPriority() {
      return -200;
   }

   public @Nullable String getProperty(String key) {
      return this.properties.get(key);
   }

   public boolean containsProperty(String key) {
      return this.properties.containsKey(key);
   }

   public Collection<String> getPropertyNames() {
      return this.properties.keySet();
   }
}
