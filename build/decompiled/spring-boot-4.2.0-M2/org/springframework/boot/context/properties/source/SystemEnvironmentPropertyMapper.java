package org.springframework.boot.context.properties.source;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiPredicate;
import org.springframework.util.ConcurrentReferenceHashMap;

final class SystemEnvironmentPropertyMapper implements PropertyMapper {
   public static final PropertyMapper INSTANCE = new SystemEnvironmentPropertyMapper();
   private final Map<String, ConfigurationPropertyName> propertySourceNameCache = new ConcurrentReferenceHashMap();

   @Override
   public List<String> map(ConfigurationPropertyName configurationPropertyName) {
      List<String> mapped = new ArrayList<>(4);
      this.addIfMissing(mapped, configurationPropertyName.toString(ConfigurationPropertyName.ToStringFormat.SYSTEM_ENVIRONMENT, true));
      this.addIfMissing(mapped, configurationPropertyName.toString(ConfigurationPropertyName.ToStringFormat.LEGACY_SYSTEM_ENVIRONMENT, true));
      this.addIfMissing(mapped, configurationPropertyName.toString(ConfigurationPropertyName.ToStringFormat.SYSTEM_ENVIRONMENT, false));
      this.addIfMissing(mapped, configurationPropertyName.toString(ConfigurationPropertyName.ToStringFormat.LEGACY_SYSTEM_ENVIRONMENT, false));
      return mapped;
   }

   private void addIfMissing(List<String> list, String value) {
      if (!list.contains(value)) {
         list.add(value);
      }
   }

   @Override
   public ConfigurationPropertyName map(String propertySourceName) {
      ConfigurationPropertyName configurationPropertyName = this.propertySourceNameCache.get(propertySourceName);
      if (configurationPropertyName == null) {
         configurationPropertyName = this.convertName(propertySourceName);
         this.propertySourceNameCache.put(propertySourceName, configurationPropertyName);
      }

      return configurationPropertyName;
   }

   private ConfigurationPropertyName convertName(String propertySourceName) {
      try {
         return ConfigurationPropertyName.adapt(propertySourceName, '_', this::processElementValue);
      } catch (Exception ex) {
         return ConfigurationPropertyName.EMPTY;
      }
   }

   private CharSequence processElementValue(CharSequence value) {
      String result = value.toString().toLowerCase(Locale.ENGLISH);
      return isNumber(result) ? "[" + result + "]" : result;
   }

   private static boolean isNumber(String string) {
      return string.chars().allMatch(Character::isDigit);
   }

   @Override
   public BiPredicate<ConfigurationPropertyName, ConfigurationPropertyName> getAncestorOfCheck() {
      return this::isAncestorOf;
   }

   private boolean isAncestorOf(ConfigurationPropertyName name, ConfigurationPropertyName candidate) {
      return name.isAncestorOf(candidate) || this.isLegacyAncestorOf(name, candidate);
   }

   private boolean isLegacyAncestorOf(ConfigurationPropertyName name, ConfigurationPropertyName candidate) {
      if (!name.hasDashedElement()) {
         return false;
      }

      ConfigurationPropertyName legacyCompatibleName = name.asSystemEnvironmentLegacyName();
      return legacyCompatibleName != null && legacyCompatibleName.isAncestorOf(candidate);
   }
}
