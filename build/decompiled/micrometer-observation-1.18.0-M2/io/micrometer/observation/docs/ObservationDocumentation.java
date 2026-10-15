package io.micrometer.observation.docs;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.ObservationRegistry;
import java.util.Objects;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public interface ObservationDocumentation {
   KeyName[] EMPTY = new KeyName[0];
   Observation.Event[] EMPTY_EVENT_NAMES = new Observation.Event[0];

   default @Nullable String getName() {
      return null;
   }

   default @Nullable Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
      return null;
   }

   default @Nullable String getContextualName() {
      return null;
   }

   default KeyName[] getLowCardinalityKeyNames() {
      return EMPTY;
   }

   default KeyName[] getHighCardinalityKeyNames() {
      return EMPTY;
   }

   default Observation.Event[] getEvents() {
      return EMPTY_EVENT_NAMES;
   }

   default String getPrefix() {
      return "";
   }

   default Observation observation(ObservationRegistry registry) {
      return this.observation(registry, Observation.Context::new);
   }

   default Observation observation(ObservationRegistry registry, Supplier<Observation.Context> contextSupplier) {
      Observation observation = Observation.createNotStarted(this.getName(), contextSupplier, registry);
      if (this.getContextualName() != null) {
         observation.contextualName(this.getContextualName());
      }

      return observation;
   }

   default <T extends Observation.Context> Observation observation(
      @Nullable ObservationConvention<T> customConvention,
      ObservationConvention<T> defaultConvention,
      Supplier<T> contextSupplier,
      ObservationRegistry registry
   ) {
      if (this.getDefaultConvention() == null) {
         throw new IllegalStateException(
            "You've decided to use convention based naming yet this observation [" + this.getClass() + "] has not defined any default convention"
         );
      }

      if (!this.getDefaultConvention()
         .isAssignableFrom(Objects.requireNonNull(defaultConvention, "You have not provided a default convention in the Observation factory method").getClass())
         )
       {
         throw new IllegalArgumentException(
            "Observation ["
               + this.getClass()
               + "] defined default convention to be of type ["
               + this.getDefaultConvention()
               + "] but you have provided an incompatible one of type ["
               + defaultConvention.getClass()
               + "]"
         );
      }

      Observation observation = Observation.createNotStarted(customConvention, defaultConvention, contextSupplier, registry);
      if (this.getName() != null) {
         observation.getContext().setName(this.getName());
      }

      if (this.getContextualName() != null) {
         observation.contextualName(this.getContextualName());
      }

      return observation;
   }

   default Observation start(ObservationRegistry registry) {
      return this.start(registry, Observation.Context::new);
   }

   default Observation start(ObservationRegistry registry, Supplier<Observation.Context> contextSupplier) {
      return this.observation(registry, contextSupplier).start();
   }

   default <T extends Observation.Context> Observation start(
      @Nullable ObservationConvention<T> customConvention,
      ObservationConvention<T> defaultConvention,
      Supplier<T> contextSupplier,
      ObservationRegistry registry
   ) {
      return this.observation(customConvention, defaultConvention, contextSupplier, registry).start();
   }
}
