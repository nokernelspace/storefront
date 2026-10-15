package org.springframework.boot.context.properties.source;

import java.util.stream.Stream;

class PrefixedIterableConfigurationPropertySource extends PrefixedConfigurationPropertySource implements IterableConfigurationPropertySource {
   PrefixedIterableConfigurationPropertySource(IterableConfigurationPropertySource source, String prefix) {
      super(source, prefix);
   }

   @Override
   public Stream<ConfigurationPropertyName> stream() {
      return this.getSource().stream().map(this::stripPrefix);
   }

   private ConfigurationPropertyName stripPrefix(ConfigurationPropertyName name) {
      return this.getPrefix().isAncestorOf(name) ? name.subName(this.getPrefix().getNumberOfElements()) : name;
   }

   protected IterableConfigurationPropertySource getSource() {
      return (IterableConfigurationPropertySource)super.getSource();
   }
}
