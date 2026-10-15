package io.micrometer.common.annotation;

import org.jspecify.annotations.Nullable;

public class NoOpValueResolver implements ValueResolver {
   @Override
   public String resolve(@Nullable Object parameter) {
      return "";
   }
}
