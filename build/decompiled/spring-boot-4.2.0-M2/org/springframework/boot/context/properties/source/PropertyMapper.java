package org.springframework.boot.context.properties.source;

import java.util.List;
import java.util.function.BiPredicate;

interface PropertyMapper {
   BiPredicate<ConfigurationPropertyName, ConfigurationPropertyName> DEFAULT_ANCESTOR_OF_CHECK = ConfigurationPropertyName::isAncestorOf;

   List<String> map(ConfigurationPropertyName configurationPropertyName);

   ConfigurationPropertyName map(String propertySourceName);

   default BiPredicate<ConfigurationPropertyName, ConfigurationPropertyName> getAncestorOfCheck() {
      return DEFAULT_ANCESTOR_OF_CHECK;
   }
}
