package org.springframework.boot.context.properties.bind;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface PlaceholdersResolver {
   PlaceholdersResolver NONE = value -> value;

   @Nullable Object resolvePlaceholders(@Nullable Object value);
}
