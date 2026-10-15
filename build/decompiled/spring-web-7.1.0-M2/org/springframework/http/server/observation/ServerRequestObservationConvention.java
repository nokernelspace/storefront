package org.springframework.http.server.observation;

import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.Observation.Context;

public interface ServerRequestObservationConvention extends ObservationConvention<ServerRequestObservationContext> {
   default boolean supportsContext(Context context) {
      return context instanceof ServerRequestObservationContext;
   }
}
