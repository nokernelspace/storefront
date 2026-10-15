package io.micrometer.observation;

import java.util.Collection;
import java.util.Collections;
import org.jspecify.annotations.Nullable;

final class NoopObservationConfig extends ObservationRegistry.ObservationConfig {
   static final NoopObservationConfig INSTANCE = new NoopObservationConfig();

   private NoopObservationConfig() {
   }

   @Override
   public ObservationRegistry.ObservationConfig observationHandler(ObservationHandler<?> handler) {
      return this;
   }

   @Override
   public ObservationRegistry.ObservationConfig observationPredicate(ObservationPredicate predicate) {
      return this;
   }

   @Override
   public boolean isObservationEnabled(@Nullable String name, Observation.@Nullable Context context) {
      return false;
   }

   @Override
   Collection<ObservationHandler<?>> getObservationHandlers() {
      return Collections.emptyList();
   }
}
