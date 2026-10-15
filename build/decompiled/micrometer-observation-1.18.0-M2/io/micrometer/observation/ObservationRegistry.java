package io.micrometer.observation;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;

public interface ObservationRegistry {
   ObservationRegistry NOOP = new ObservationRegistry() {
      @Override
      public @Nullable Observation getCurrentObservation() {
         return SimpleObservationRegistry._getCurrentObservation();
      }

      @Override
      public Observation.@Nullable Scope getCurrentObservationScope() {
         return SimpleObservationRegistry._getCurrentObservationScope();
      }

      @Override
      public void setCurrentObservationScope(Observation.@Nullable Scope current) {
         SimpleObservationRegistry._setCurrentObservationScope(current);
      }

      @Override
      public ObservationRegistry.ObservationConfig observationConfig() {
         return NoopObservationConfig.INSTANCE;
      }

      @Override
      public String toString() {
         return "ObservationRegistry.NOOP";
      }
   };

   static ObservationRegistry create() {
      return new SimpleObservationRegistry();
   }

   @Nullable Observation getCurrentObservation();

   Observation.@Nullable Scope getCurrentObservationScope();

   void setCurrentObservationScope(Observation.@Nullable Scope var1);

   ObservationRegistry.ObservationConfig observationConfig();

   default boolean isNoop() {
      return this == NOOP;
   }

   class ObservationConfig {
      private final List<ObservationHandler<?>> observationHandlers = new CopyOnWriteArrayList<>();
      private final List<ObservationPredicate> observationPredicates = new CopyOnWriteArrayList<>();
      private final List<ObservationConvention<?>> observationConventions = new CopyOnWriteArrayList<>();
      private final List<ObservationFilter> observationFilters = new CopyOnWriteArrayList<>();

      public ObservationRegistry.ObservationConfig observationHandler(ObservationHandler<?> handler) {
         this.observationHandlers.add(handler);
         return this;
      }

      public ObservationRegistry.ObservationConfig observationPredicate(ObservationPredicate predicate) {
         this.observationPredicates.add(predicate);
         return this;
      }

      public ObservationRegistry.ObservationConfig observationFilter(ObservationFilter observationFilter) {
         this.observationFilters.add(observationFilter);
         return this;
      }

      public ObservationRegistry.ObservationConfig observationConvention(GlobalObservationConvention<?> observationConvention) {
         this.observationConventions.add(observationConvention);
         return this;
      }

      <T extends Observation.Context> ObservationConvention<T> getObservationConvention(T context, ObservationConvention<T> defaultConvention) {
         for (ObservationConvention<?> convention : this.observationConventions) {
            if (convention.supportsContext(context)) {
               return (ObservationConvention<T>)convention;
            }
         }

         return Objects.requireNonNull(defaultConvention, "Default ObservationConvention must not be null");
      }

      boolean isObservationEnabled(@Nullable String name, Observation.@Nullable Context context) {
         for (ObservationPredicate predicate : this.observationPredicates) {
            if (!predicate.test(name, context)) {
               return false;
            }
         }

         return true;
      }

      Collection<ObservationHandler<?>> getObservationHandlers() {
         return this.observationHandlers;
      }

      Collection<ObservationFilter> getObservationFilters() {
         return this.observationFilters;
      }

      Collection<ObservationConvention<?>> getObservationConventions() {
         return this.observationConventions;
      }
   }
}
