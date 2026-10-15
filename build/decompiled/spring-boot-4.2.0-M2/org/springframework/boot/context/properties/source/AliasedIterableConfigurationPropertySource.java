package org.springframework.boot.context.properties.source;

import java.util.List;
import java.util.stream.Stream;
import org.springframework.util.CollectionUtils;

class AliasedIterableConfigurationPropertySource extends AliasedConfigurationPropertySource implements IterableConfigurationPropertySource {
   AliasedIterableConfigurationPropertySource(IterableConfigurationPropertySource source, ConfigurationPropertyNameAliases aliases) {
      super(source, aliases);
   }

   @Override
   public Stream<ConfigurationPropertyName> stream() {
      return this.getSource().stream().flatMap(this::addAliases);
   }

   private Stream<ConfigurationPropertyName> addAliases(ConfigurationPropertyName name) {
      Stream<ConfigurationPropertyName> names = Stream.of(name);
      List<ConfigurationPropertyName> aliases = this.getAliases().getAliases(name);
      return CollectionUtils.isEmpty(aliases) ? names : Stream.concat(names, aliases.stream());
   }

   protected IterableConfigurationPropertySource getSource() {
      return (IterableConfigurationPropertySource)super.getSource();
   }
}
