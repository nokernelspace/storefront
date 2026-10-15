package io.micrometer.observation;

import org.jspecify.annotations.Nullable;

public interface ObservationView {
   default ObservationRegistry getObservationRegistry() {
      return ObservationRegistry.NOOP;
   }

   Observation.ContextView getContextView();

   @Deprecated
   default Observation.@Nullable Scope getEnclosingScope() {
      return Observation.Scope.NOOP;
   }
}
