package io.micrometer.common.annotation;

import org.jspecify.annotations.Nullable;

public interface ValueResolver {
   String resolve(@Nullable Object var1);
}
