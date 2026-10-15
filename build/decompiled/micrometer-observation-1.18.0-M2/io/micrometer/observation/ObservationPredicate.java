package io.micrometer.observation;

import java.util.function.BiPredicate;

public interface ObservationPredicate extends BiPredicate<String, Observation.Context> {
}
