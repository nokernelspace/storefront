package io.micrometer.observation;

public class NullObservation extends SimpleObservation {
   public NullObservation(ObservationRegistry registry) {
      super("null", registry, new NullObservation.NullContext());
   }

   @Override
   void notifyOnObservationStarted() {
   }

   @Override
   void notifyOnError() {
   }

   @Override
   void notifyOnEvent(Observation.Event event) {
   }

   @Override
   void notifyOnObservationStopped(Observation.Context context) {
   }

   @Override
   public Observation start() {
      return this;
   }

   public static class NullContext extends Observation.Context {
   }
}
