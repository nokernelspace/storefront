package org.springframework.boot.context.properties.bind;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;

@FunctionalInterface
interface AggregateElementBinder {
   default @Nullable Object bind(ConfigurationPropertyName name, Bindable<?> target) {
      return this.bind(name, target, null);
   }

   @Nullable Object bind(ConfigurationPropertyName name, Bindable<?> target, @Nullable ConfigurationPropertySource source);
}
