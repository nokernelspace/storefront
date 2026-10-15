package org.springframework.util;

import java.util.Properties;
import org.jspecify.annotations.Nullable;

public class PropertyPlaceholderHelper {
   private final PlaceholderParser parser;

   public PropertyPlaceholderHelper(String placeholderPrefix, String placeholderSuffix) {
      this(placeholderPrefix, placeholderSuffix, null, null, true);
   }

   public PropertyPlaceholderHelper(
      String placeholderPrefix,
      String placeholderSuffix,
      @Nullable String valueSeparator,
      @Nullable Character escapeCharacter,
      boolean ignoreUnresolvablePlaceholders
   ) {
      Assert.notNull(placeholderPrefix, "'placeholderPrefix' must not be null");
      Assert.notNull(placeholderSuffix, "'placeholderSuffix' must not be null");
      this.parser = new PlaceholderParser(placeholderPrefix, placeholderSuffix, valueSeparator, escapeCharacter, ignoreUnresolvablePlaceholders);
   }

   public String replacePlaceholders(String value, Properties properties) {
      Assert.notNull(properties, "'properties' must not be null");
      return this.replacePlaceholders(value, properties::getProperty);
   }

   public String replacePlaceholders(String value, PropertyPlaceholderHelper.PlaceholderResolver placeholderResolver) {
      Assert.notNull(value, "'value' must not be null");
      return this.parser.replacePlaceholders(value, placeholderResolver);
   }

   @FunctionalInterface
   public interface PlaceholderResolver {
      @Nullable String resolvePlaceholder(String placeholderName);
   }
}
