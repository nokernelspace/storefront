package org.springframework.core.env;

import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public class SystemEnvironmentPropertySource extends MapPropertySource {
   public SystemEnvironmentPropertySource(String name, Map<String, Object> source) {
      super(name, source);
   }

   @Override
   public boolean containsProperty(String name) {
      return this.getProperty(name) != null;
   }

   @Override
   public @Nullable Object getProperty(String name) {
      String actualName = this.resolvePropertyName(name);
      if (this.logger.isDebugEnabled() && !name.equals(actualName)) {
         this.logger.debug("PropertySource '" + this.getName() + "' does not contain property '" + name + "', but found equivalent '" + actualName + "'");
      }

      return super.getProperty(actualName);
   }

   protected final String resolvePropertyName(String name) {
      Assert.notNull(name, "Property name must not be null");
      String resolvedName = this.checkPropertyName(name);
      if (resolvedName != null) {
         return resolvedName;
      }

      String uppercasedName = name.toUpperCase(Locale.ROOT);
      if (!name.equals(uppercasedName)) {
         resolvedName = this.checkPropertyName(uppercasedName);
         if (resolvedName != null) {
            return resolvedName;
         }
      }

      return name;
   }

   private @Nullable String checkPropertyName(String name) {
      if (this.source.containsKey(name)) {
         return name;
      }

      String noDotName = name.replace('.', '_');
      if (!name.equals(noDotName) && this.source.containsKey(noDotName)) {
         return noDotName;
      }

      String noHyphenName = name.replace('-', '_');
      if (!name.equals(noHyphenName) && this.source.containsKey(noHyphenName)) {
         return noHyphenName;
      }

      String noDotNoHyphenName = noDotName.replace('-', '_');
      return !noDotName.equals(noDotNoHyphenName) && this.source.containsKey(noDotNoHyphenName) ? noDotNoHyphenName : null;
   }
}
