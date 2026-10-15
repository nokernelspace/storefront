package org.springframework.util;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface StringValueResolver {
   @Nullable String resolveStringValue(String strVal);
}
