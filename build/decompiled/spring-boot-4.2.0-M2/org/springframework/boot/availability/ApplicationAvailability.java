package org.springframework.boot.availability;

import org.jspecify.annotations.Nullable;

public interface ApplicationAvailability {
   default LivenessState getLivenessState() {
      return this.getState(LivenessState.class, LivenessState.BROKEN);
   }

   default ReadinessState getReadinessState() {
      return this.getState(ReadinessState.class, ReadinessState.REFUSING_TRAFFIC);
   }

   <S extends AvailabilityState> S getState(Class<S> stateType, S defaultState);

   <S extends AvailabilityState> @Nullable S getState(Class<S> stateType);

   <S extends AvailabilityState> @Nullable AvailabilityChangeEvent<S> getLastChangeEvent(Class<S> stateType);
}
