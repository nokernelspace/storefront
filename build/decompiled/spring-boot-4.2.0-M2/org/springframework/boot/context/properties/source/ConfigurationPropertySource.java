package org.springframework.boot.context.properties.source;

import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.core.env.PropertySource;
import org.springframework.util.StringUtils;

@FunctionalInterface
public interface ConfigurationPropertySource {
   @Nullable ConfigurationProperty getConfigurationProperty(ConfigurationPropertyName name);

   default ConfigurationPropertyState containsDescendantOf(ConfigurationPropertyName name) {
      return ConfigurationPropertyState.UNKNOWN;
   }

   default ConfigurationPropertySource filter(Predicate<ConfigurationPropertyName> filter) {
      return new FilteredConfigurationPropertiesSource(this, filter);
   }

   default ConfigurationPropertySource withAliases(ConfigurationPropertyNameAliases aliases) {
      return new AliasedConfigurationPropertySource(this, aliases);
   }

   default ConfigurationPropertySource withPrefix(@Nullable String prefix) {
      return StringUtils.hasText(prefix) ? new PrefixedConfigurationPropertySource(this, prefix) : this;
   }

   default @Nullable Object getUnderlyingSource() {
      return null;
   }

   static @Nullable ConfigurationPropertySource from(PropertySource<?> source) {
      return source instanceof ConfigurationPropertySourcesPropertySource ? null : SpringConfigurationPropertySource.from(source);
   }
}
