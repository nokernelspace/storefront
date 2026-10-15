package org.springframework.boot.context.properties.source;

import java.util.Arrays;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

class FilteredIterableConfigurationPropertiesSource extends FilteredConfigurationPropertiesSource implements IterableConfigurationPropertySource {
   private ConfigurationPropertyName @Nullable [] filteredNames;
   private int numberOfFilteredNames;

   FilteredIterableConfigurationPropertiesSource(IterableConfigurationPropertySource source, Predicate<ConfigurationPropertyName> filter) {
      super(source, filter);
      ConfigurationPropertyName[] filterableNames = this.getFilterableNames(source);
      if (filterableNames != null) {
         this.filteredNames = new ConfigurationPropertyName[filterableNames.length];
         this.numberOfFilteredNames = 0;

         for (ConfigurationPropertyName name : filterableNames) {
            if (name == null) {
               break;
            }

            if (filter.test(name)) {
               this.filteredNames[this.numberOfFilteredNames++] = name;
            }
         }
      }
   }

   private @Nullable ConfigurationPropertyName @Nullable [] getFilterableNames(IterableConfigurationPropertySource source) {
      if (source instanceof SpringIterableConfigurationPropertySource springPropertySource && springPropertySource.isImmutablePropertySource()) {
         return springPropertySource.getConfigurationPropertyNames();
      } else {
         return source instanceof FilteredIterableConfigurationPropertiesSource filteredSource ? filteredSource.filteredNames : null;
      }
   }

   @Override
   public Stream<ConfigurationPropertyName> stream() {
      return this.filteredNames != null ? Arrays.stream(this.filteredNames, 0, this.numberOfFilteredNames) : this.getSource().stream().filter(this.getFilter());
   }

   protected IterableConfigurationPropertySource getSource() {
      return (IterableConfigurationPropertySource)super.getSource();
   }

   @Override
   public ConfigurationPropertyState containsDescendantOf(ConfigurationPropertyName name) {
      return this.filteredNames != null
         ? ConfigurationPropertyState.search(this.filteredNames, 0, this.numberOfFilteredNames, name::isAncestorOf)
         : ConfigurationPropertyState.search(this, name::isAncestorOf);
   }
}
