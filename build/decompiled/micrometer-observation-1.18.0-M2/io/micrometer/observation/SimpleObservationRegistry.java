package io.micrometer.observation;

import org.jspecify.annotations.Nullable;

class SimpleObservationRegistry implements ObservationRegistry {
   private static final ThreadLocal<Observation.@Nullable Scope> localObservationScope = new ThreadLocal<>();
   private final ObservationRegistry.ObservationConfig observationConfig = new ObservationRegistry.ObservationConfig();

   @Override
   public @Nullable Observation getCurrentObservation() {
      return _getCurrentObservation();
   }

   static @Nullable Observation _getCurrentObservation() {
      Observation.Scope scope = localObservationScope.get();
      return scope != null ? scope.getCurrentObservation() : null;
   }

   @Override
   public Observation.@Nullable Scope getCurrentObservationScope() {
      return _getCurrentObservationScope();
   }

   static Observation.@Nullable Scope _getCurrentObservationScope() {
      return localObservationScope.get();
   }

   @Override
   public void setCurrentObservationScope(Observation.@Nullable Scope current) {
      _setCurrentObservationScope(current);
   }

   static void _setCurrentObservationScope(Observation.@Nullable Scope current) {
      localObservationScope.set(current);
   }

   @Override
   public ObservationRegistry.ObservationConfig observationConfig() {
      return this.observationConfig;
   }

   @Override
   public boolean isNoop() {
      return ObservationRegistry.super.isNoop() || this.observationConfig().getObservationHandlers().isEmpty();
   }
}
