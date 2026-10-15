package org.springframework.core.annotation;

import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
interface ValueExtractor {
   @Nullable Object extract(Method attribute, @Nullable Object object);
}
