package org.springframework.core.env;

import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;

public interface PropertySources extends Iterable<PropertySource<?>> {
   default Stream<PropertySource<?>> stream() {
      return StreamSupport.stream(this.spliterator(), false);
   }

   boolean contains(String name);

   @Nullable PropertySource<?> get(String name);
}
