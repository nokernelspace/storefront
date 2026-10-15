package io.micrometer.observation;

public interface ObservationFilter {
   Observation.Context map(Observation.Context var1);
}
