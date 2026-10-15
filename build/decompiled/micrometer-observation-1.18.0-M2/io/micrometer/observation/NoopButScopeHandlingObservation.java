package io.micrometer.observation;

import io.micrometer.common.KeyValue;
import org.jspecify.annotations.Nullable;

final class NoopButScopeHandlingObservation implements Observation {
   static final NoopButScopeHandlingObservation INSTANCE = new NoopButScopeHandlingObservation();

   @Override
   public Observation contextualName(@Nullable String contextualName) {
      return this;
   }

   @Override
   public Observation parentObservation(@Nullable Observation parentObservation) {
      return this;
   }

   @Override
   public Observation lowCardinalityKeyValue(KeyValue keyValue) {
      return this;
   }

   @Override
   public Observation lowCardinalityKeyValue(String key, String value) {
      return this;
   }

   @Override
   public Observation highCardinalityKeyValue(KeyValue keyValue) {
      return this;
   }

   @Override
   public Observation highCardinalityKeyValue(String key, String value) {
      return this;
   }

   @Override
   public Observation observationConvention(ObservationConvention<?> observationConvention) {
      return this;
   }

   @Override
   public Observation error(Throwable error) {
      return this;
   }

   @Override
   public Observation event(Observation.Event event) {
      return this;
   }

   @Override
   public Observation start() {
      return this;
   }

   @Override
   public Observation.Context getContext() {
      return NoopContext.INSTANCE;
   }

   @Override
   public void stop() {
   }

   @Override
   public Observation.Scope openScope() {
      return new SimpleObservation.SimpleScope(ObservationRegistry.NOOP, this);
   }
}
