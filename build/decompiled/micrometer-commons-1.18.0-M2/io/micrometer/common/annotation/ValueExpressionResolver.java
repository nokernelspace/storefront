package io.micrometer.common.annotation;

import org.jspecify.annotations.Nullable;

public interface ValueExpressionResolver {
   String resolve(String var1, @Nullable Object var2);
}
