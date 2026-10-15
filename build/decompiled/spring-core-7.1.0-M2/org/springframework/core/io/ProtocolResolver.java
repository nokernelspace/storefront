package org.springframework.core.io;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface ProtocolResolver {
   @Nullable Resource resolve(String location, ResourceLoader resourceLoader);
}
