package org.springframework.core.convert.converter;

import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

@FunctionalInterface
public interface Converter<S, T> {
   T convert(S source);

   default <U> Converter<S, @Nullable U> andThen(Converter<? super T, ? extends @Nullable U> after) {
      Assert.notNull(after, "'after' Converter must not be null");
      return s -> {
         T initialResult = this.convert(s);
         return (U)(initialResult != null ? after.convert(initialResult) : null);
      };
   }
}
