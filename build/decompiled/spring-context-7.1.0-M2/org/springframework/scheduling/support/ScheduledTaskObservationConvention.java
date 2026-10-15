package org.springframework.scheduling.support;

import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.Observation.Context;

public interface ScheduledTaskObservationConvention extends ObservationConvention<ScheduledTaskObservationContext> {
   default boolean supportsContext(Context context) {
      return context instanceof ScheduledTaskObservationContext;
   }
}
