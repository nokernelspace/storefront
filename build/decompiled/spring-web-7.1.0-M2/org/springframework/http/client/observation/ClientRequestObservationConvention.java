package org.springframework.http.client.observation;

import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.Observation.Context;

public interface ClientRequestObservationConvention extends ObservationConvention<ClientRequestObservationContext> {
   default boolean supportsContext(Context context) {
      return context instanceof ClientRequestObservationContext;
   }
}
