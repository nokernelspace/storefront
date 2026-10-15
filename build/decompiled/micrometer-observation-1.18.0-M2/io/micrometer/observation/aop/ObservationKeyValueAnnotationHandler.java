package io.micrometer.observation.aop;

import io.micrometer.common.KeyValue;
import io.micrometer.common.annotation.AnnotationHandler;
import io.micrometer.common.annotation.ValueExpressionResolver;
import io.micrometer.common.annotation.ValueResolver;
import io.micrometer.observation.Observation;
import io.micrometer.observation.annotation.ObservationKeyValue;
import java.lang.annotation.Annotation;
import java.util.function.Function;

public class ObservationKeyValueAnnotationHandler extends AnnotationHandler<Observation.Context> {
   public ObservationKeyValueAnnotationHandler(
      Function<Class<? extends ValueResolver>, ? extends ValueResolver> resolverProvider,
      Function<Class<? extends ValueExpressionResolver>, ? extends ValueExpressionResolver> expressionResolverProvider
   ) {
      super(
         ObservationKeyValueAnnotationHandler::addKeyValue,
         resolverProvider,
         expressionResolverProvider,
         ObservationKeyValue.class,
         (annotation, object) -> toKeyValue(annotation, object, resolverProvider, expressionResolverProvider)
      );
   }

   private static void addKeyValue(KeyValue keyValue, Observation.Context context) {
      if (keyValue instanceof ObservationKeyValueAnnotationHandler.KeyValueWithCardinality) {
         ObservationKeyValueAnnotationHandler.KeyValueWithCardinality keyValueWithCardinality = (ObservationKeyValueAnnotationHandler.KeyValueWithCardinality)keyValue;
         if (keyValueWithCardinality.cardinality == Cardinality.LOW) {
            context.addLowCardinalityKeyValue(keyValueWithCardinality.getDelegate());
         } else {
            context.addHighCardinalityKeyValue(keyValueWithCardinality.getDelegate());
         }
      }
   }

   private static ObservationKeyValueAnnotationHandler.KeyValueWithCardinality toKeyValue(
      Annotation annotation,
      Object object,
      Function<Class<? extends ValueResolver>, ? extends ValueResolver> resolverProvider,
      Function<Class<? extends ValueExpressionResolver>, ? extends ValueExpressionResolver> expressionResolverProvider
   ) {
      ObservationKeyValue observationKeyValue = (ObservationKeyValue)annotation;
      KeyValue keyValue = KeyValue.of(
         ObservationKeyValueSupport.resolveKey(observationKeyValue),
         ObservationKeyValueSupport.resolveValue(observationKeyValue, object, resolverProvider, expressionResolverProvider)
      );
      return new ObservationKeyValueAnnotationHandler.KeyValueWithCardinality(keyValue, observationKeyValue.cardinality());
   }

   private static class KeyValueWithCardinality implements KeyValue {
      private final KeyValue keyValue;
      private final Cardinality cardinality;

      private KeyValueWithCardinality(KeyValue keyValue, Cardinality cardinality) {
         this.keyValue = keyValue;
         this.cardinality = cardinality;
      }

      public String getKey() {
         return this.keyValue.getKey();
      }

      public String getValue() {
         return this.keyValue.getValue();
      }

      private KeyValue getDelegate() {
         return this.keyValue;
      }
   }
}
