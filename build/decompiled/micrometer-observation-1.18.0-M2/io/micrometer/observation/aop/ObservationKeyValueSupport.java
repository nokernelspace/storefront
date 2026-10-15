package io.micrometer.observation.aop;

import io.micrometer.common.annotation.NoOpValueResolver;
import io.micrometer.common.annotation.ValueExpressionResolver;
import io.micrometer.common.annotation.ValueResolver;
import io.micrometer.common.util.StringUtils;
import io.micrometer.observation.annotation.ObservationKeyValue;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

class ObservationKeyValueSupport {
   private ObservationKeyValueSupport() {
   }

   static String resolveKey(ObservationKeyValue observationKeyValue) {
      return StringUtils.isNotBlank(observationKeyValue.value()) ? observationKeyValue.value() : observationKeyValue.key();
   }

   static String resolveValue(
      ObservationKeyValue annotation,
      @Nullable Object argument,
      Function<Class<? extends ValueResolver>, ? extends ValueResolver> resolverProvider,
      Function<Class<? extends ValueExpressionResolver>, ? extends ValueExpressionResolver> expressionResolverProvider
   ) {
      String value = null;
      if (annotation.resolver() != NoOpValueResolver.class) {
         ValueResolver valueResolver = resolverProvider.apply(annotation.resolver());
         value = valueResolver.resolve(argument);
      } else if (StringUtils.isNotBlank(annotation.expression())) {
         value = expressionResolverProvider.apply(ValueExpressionResolver.class).resolve(annotation.expression(), argument);
      } else if (argument != null) {
         value = argument.toString();
      }

      return value == null ? "" : value;
   }
}
