package org.springframework.boot.availability;

public enum LivenessState implements AvailabilityState {
   CORRECT,
   BROKEN;
}
