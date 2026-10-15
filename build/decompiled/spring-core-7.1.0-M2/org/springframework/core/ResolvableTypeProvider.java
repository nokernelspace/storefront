package org.springframework.core;

import org.jspecify.annotations.Nullable;

public interface ResolvableTypeProvider {
   @Nullable ResolvableType getResolvableType();
}
