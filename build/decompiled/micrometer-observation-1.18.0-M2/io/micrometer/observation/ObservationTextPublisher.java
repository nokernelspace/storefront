package io.micrometer.observation;

import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class ObservationTextPublisher implements ObservationHandler<Observation.Context> {
   private final Consumer<String> consumer;
   private final Predicate<Observation.Context> supportsContextPredicate;
   private final Function<Observation.Context, String> converter;

   public ObservationTextPublisher() {
      this(InternalLoggerFactory.getInstance(ObservationTextPublisher.class)::info, context -> true, String::valueOf);
   }

   public ObservationTextPublisher(Consumer<String> consumer) {
      this(consumer, context -> true, String::valueOf);
   }

   public ObservationTextPublisher(Consumer<String> consumer, Predicate<Observation.Context> supportsContextPredicate) {
      this(consumer, supportsContextPredicate, String::valueOf);
   }

   public ObservationTextPublisher(
      Consumer<String> consumer, Predicate<Observation.Context> supportsContextPredicate, Function<Observation.Context, String> converter
   ) {
      this.consumer = consumer;
      this.supportsContextPredicate = supportsContextPredicate;
      this.converter = converter;
   }

   @Override
   public void onStart(Observation.Context context) {
      this.publish("START", context);
   }

   @Override
   public void onError(Observation.Context context) {
      this.publish("ERROR", context);
   }

   @Override
   public void onEvent(Observation.Event event, Observation.Context context) {
      this.publishUnformatted(String.format("%5s - %s, %s", "EVENT", event, this.converter.apply(context)));
   }

   @Override
   public void onScopeOpened(Observation.Context context) {
      this.publish("OPEN", context);
   }

   @Override
   public void onScopeClosed(Observation.Context context) {
      this.publish("CLOSE", context);
   }

   @Override
   public void onStop(Observation.Context context) {
      this.publish("STOP", context);
   }

   @Override
   public boolean supportsContext(Observation.Context context) {
      return this.supportsContextPredicate.test(context);
   }

   private void publish(String event, Observation.Context context) {
      this.publishUnformatted(String.format("%5s - %s", event, this.converter.apply(context)));
   }

   private void publishUnformatted(String event) {
      this.consumer.accept(event);
   }
}
