package io.micrometer.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

class NoopContext extends Observation.Context {
   static final NoopContext INSTANCE = new NoopContext();

   private NoopContext() {
   }

   @Override
   public void setName(String name) {
   }

   @Override
   public void setContextualName(@Nullable String contextualName) {
   }

   @Override
   public void setParentObservation(@Nullable ObservationView parentObservation) {
   }

   @Override
   void setParentFromCurrentObservation(ObservationRegistry registry) {
   }

   @Override
   public void setError(Throwable error) {
   }

   @Override
   public <T> Observation.Context put(Object key, T object) {
      return this;
   }

   @Override
   public @Nullable Object remove(Object key) {
      return null;
   }

   @Override
   public <T> T computeIfAbsent(Object key, Function<Object, ? extends T> mappingFunction) {
      return (T)mappingFunction.apply(key);
   }

   @Override
   public void clear() {
   }

   @Override
   public Observation.Context addLowCardinalityKeyValue(KeyValue keyValue) {
      return this;
   }

   @Override
   public Observation.Context addHighCardinalityKeyValue(KeyValue keyValue) {
      return this;
   }

   @Override
   public Observation.Context removeLowCardinalityKeyValue(String keyName) {
      return this;
   }

   @Override
   public Observation.Context removeHighCardinalityKeyValue(String keyName) {
      return this;
   }

   @Override
   public Observation.Context addLowCardinalityKeyValues(KeyValues keyValues) {
      return this;
   }

   @Override
   public Observation.Context addHighCardinalityKeyValues(KeyValues keyValues) {
      return this;
   }

   @Override
   public Observation.Context removeLowCardinalityKeyValues(String... keyNames) {
      return this;
   }

   @Override
   public Observation.Context removeHighCardinalityKeyValues(String... keyNames) {
      return this;
   }
}
