package org.springframework.boot.origin;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface OriginProvider {
   @Nullable Origin getOrigin();
}
