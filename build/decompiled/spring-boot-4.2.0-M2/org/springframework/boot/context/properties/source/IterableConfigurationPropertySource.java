package org.springframework.boot.context.properties.source;

import java.util.Iterator;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

public interface IterableConfigurationPropertySource extends ConfigurationPropertySource, Iterable<ConfigurationPropertyName> {
   @Override
   default Iterator<ConfigurationPropertyName> iterator() {
      return this.stream().iterator();
   }

   Stream<ConfigurationPropertyName> stream();

   @Override
   default ConfigurationPropertyState containsDescendantOf(ConfigurationPropertyName name) {
      return ConfigurationPropertyState.search(this, name::isAncestorOf);
   }

   default IterableConfigurationPropertySource filter(Predicate<ConfigurationPropertyName> filter) {
      return new FilteredIterableConfigurationPropertiesSource(this, filter);
   }

   default IterableConfigurationPropertySource withAliases(ConfigurationPropertyNameAliases aliases) {
      return new AliasedIterableConfigurationPropertySource(this, aliases);
   }

   default IterableConfigurationPropertySource withPrefix(@Nullable String prefix) {
      return StringUtils.hasText(prefix) ? new PrefixedIterableConfigurationPropertySource(this, prefix) : this;
   }
}
