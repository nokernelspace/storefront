package org.springframework.boot.availability;

public enum ReadinessState implements AvailabilityState {
   ACCEPTING_TRAFFIC,
   REFUSING_TRAFFIC;
}
