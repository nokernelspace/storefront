package io.micrometer.observation;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.jspecify.annotations.Nullable;

public final class Observations {
   private static final ObservationRegistry initialRegistry = ObservationRegistry.create();
   private static final Observations.DelegatingObservationRegistry globalRegistry = new Observations.DelegatingObservationRegistry(initialRegistry);

   private Observations() {
   }

   public static void setRegistry(ObservationRegistry registry) {
      globalRegistry.setDelegate(registry);
   }

   public static void resetRegistry() {
      globalRegistry.setDelegate(initialRegistry);
   }

   public static ObservationRegistry getGlobalRegistry() {
      return globalRegistry;
   }

   private static final class DelegatingObservationRegistry implements ObservationRegistry {
      private final AtomicReference<ObservationRegistry> delegate = new AtomicReference<>(ObservationRegistry.NOOP);

      DelegatingObservationRegistry(ObservationRegistry delegate) {
         this.setDelegate(delegate);
      }

      void setDelegate(ObservationRegistry delegate) {
         this.delegate.set(Objects.requireNonNull(delegate, "Delegate must not be null"));
      }

      @Override
      public @Nullable Observation getCurrentObservation() {
         return this.delegate.get().getCurrentObservation();
      }

      @Override
      public Observation.@Nullable Scope getCurrentObservationScope() {
         return this.delegate.get().getCurrentObservationScope();
      }

      @Override
      public void setCurrentObservationScope(Observation.@Nullable Scope current) {
         this.delegate.get().setCurrentObservationScope(current);
      }

      @Override
      public ObservationRegistry.ObservationConfig observationConfig() {
         return this.delegate.get().observationConfig();
      }

      @Override
      public boolean isNoop() {
         return this.delegate.get().isNoop();
      }
   }
}
