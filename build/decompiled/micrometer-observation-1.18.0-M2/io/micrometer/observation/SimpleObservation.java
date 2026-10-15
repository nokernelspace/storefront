package io.micrometer.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.util.StringUtils;
import io.micrometer.common.util.internal.logging.InternalLogger;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import io.micrometer.common.util.internal.logging.WarnThenDebugLogger;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import org.jspecify.annotations.Nullable;

class SimpleObservation implements Observation {
   private static final WarnThenDebugLogger getEnclosingScopeLogger = new WarnThenDebugLogger(SimpleObservation.class);
   final ObservationRegistry registry;
   private final Observation.Context context;
   private @Nullable ObservationConvention convention;
   private final Deque<ObservationHandler> handlers;
   private final Collection<ObservationFilter> filters;

   SimpleObservation(String name, ObservationRegistry registry, Observation.Context context) {
      this.registry = registry;
      this.context = context;
      this.context.setName(name);
      this.convention = getConventionFromConfig(registry, context);
      this.handlers = getHandlersFromConfig(registry, context);
      this.filters = registry.observationConfig().getObservationFilters();
   }

   SimpleObservation(ObservationConvention<? extends Observation.Context> convention, ObservationRegistry registry, Observation.Context context) {
      this.registry = registry;
      this.context = context;
      this.handlers = getHandlersFromConfig(registry, context);
      this.filters = registry.observationConfig().getObservationFilters();
      if (convention.supportsContext(context)) {
         this.convention = convention;
      } else {
         throw new IllegalStateException("Convention [" + convention + "] doesn't support context [" + context + "]");
      }
   }

   private static @Nullable ObservationConvention getConventionFromConfig(ObservationRegistry registry, Observation.Context context) {
      for (ObservationConvention<?> convention : registry.observationConfig().getObservationConventions()) {
         if (convention.supportsContext(context)) {
            return convention;
         }
      }

      return null;
   }

   private static Deque<ObservationHandler> getHandlersFromConfig(ObservationRegistry registry, Observation.Context context) {
      Collection<ObservationHandler<?>> handlers = registry.observationConfig().getObservationHandlers();
      Deque<ObservationHandler> deque = new ArrayDeque<>(handlers.size());

      for (ObservationHandler handler : handlers) {
         if (handler.supportsContext(context)) {
            deque.add(handler);
         }
      }

      return deque;
   }

   @Override
   public Observation contextualName(@Nullable String contextualName) {
      this.context.setContextualName(contextualName);
      return this;
   }

   @Override
   public Observation parentObservation(@Nullable Observation parentObservation) {
      this.context.setParentObservation(parentObservation);
      return this;
   }

   @Override
   public Observation lowCardinalityKeyValue(KeyValue keyValue) {
      this.context.addLowCardinalityKeyValue(keyValue);
      return this;
   }

   @Override
   public Observation highCardinalityKeyValue(KeyValue keyValue) {
      this.context.addHighCardinalityKeyValue(keyValue);
      return this;
   }

   @Override
   public Observation observationConvention(ObservationConvention<?> convention) {
      if (convention.supportsContext(this.context)) {
         this.convention = convention;
      }

      return this;
   }

   @Override
   public Observation error(Throwable error) {
      this.context.setError(error);
      this.notifyOnError();
      return this;
   }

   @Override
   public Observation event(Observation.Event event) {
      this.notifyOnEvent(event);
      return this;
   }

   @Override
   public Observation start() {
      if (this.convention != null) {
         this.context.addLowCardinalityKeyValues(this.convention.getLowCardinalityKeyValues(this.context));
         this.context.addHighCardinalityKeyValues(this.convention.getHighCardinalityKeyValues(this.context));
         String newName = this.convention.getName();
         if (StringUtils.isNotBlank(newName)) {
            this.context.setName(newName);
         }
      }

      this.notifyOnObservationStarted();
      return this;
   }

   @Override
   public Observation.Context getContext() {
      return this.context;
   }

   @Override
   public void stop() {
      if (this.convention != null) {
         this.context.addLowCardinalityKeyValues(this.convention.getLowCardinalityKeyValues(this.context));
         this.context.addHighCardinalityKeyValues(this.convention.getHighCardinalityKeyValues(this.context));
         String newContextualName = this.convention.getContextualName(this.context);
         if (StringUtils.isNotBlank(newContextualName)) {
            this.context.setContextualName(newContextualName);
         }
      }

      Observation.Context modifiedContext = this.context;

      for (ObservationFilter filter : this.filters) {
         modifiedContext = filter.map(modifiedContext);
      }

      this.notifyOnObservationStopped(modifiedContext);
   }

   @Override
   public Observation.Scope openScope() {
      Observation.Scope scope = new SimpleObservation.SimpleScope(this.registry, this);
      this.notifyOnScopeOpened();
      return scope;
   }

   @Deprecated
   @Override
   public Observation.@Nullable Scope getEnclosingScope() {
      getEnclosingScopeLogger.log(
         "getEnclosingScope() is deprecated, will always return a no-op scope, and will be removed in a future release. Please do not use it."
      );
      return Observation.Scope.NOOP;
   }

   @Override
   public String toString() {
      return "{name="
         + this.context.getName()
         + "("
         + this.context.getContextualName()
         + "), error="
         + this.context.getError()
         + ", context="
         + this.context
         + '}';
   }

   void notifyOnObservationStarted() {
      for (ObservationHandler handler : this.handlers) {
         handler.onStart(this.context);
      }
   }

   void notifyOnError() {
      for (ObservationHandler handler : this.handlers) {
         handler.onError(this.context);
      }
   }

   void notifyOnEvent(Observation.Event event) {
      for (ObservationHandler handler : this.handlers) {
         handler.onEvent(event, this.context);
      }
   }

   void notifyOnScopeOpened() {
      for (ObservationHandler handler : this.handlers) {
         handler.onScopeOpened(this.context);
      }
   }

   void notifyOnScopeClosed() {
      Iterator<ObservationHandler> iterator = this.handlers.descendingIterator();

      while (iterator.hasNext()) {
         ObservationHandler handler = iterator.next();
         handler.onScopeClosed(this.context);
      }
   }

   void notifyOnObservationStopped(Observation.Context context) {
      this.handlers.descendingIterator().forEachRemaining(handler -> handler.onStop(context));
   }

   @Override
   public ObservationRegistry getObservationRegistry() {
      return this.registry;
   }

   static class SimpleScope implements Observation.Scope {
      private static final InternalLogger log = InternalLoggerFactory.getInstance(SimpleObservation.SimpleScope.class);
      private static final WarnThenDebugLogger resetLogger = new WarnThenDebugLogger(SimpleObservation.SimpleScope.class);
      private static final WarnThenDebugLogger makeCurrentLogger = new WarnThenDebugLogger(SimpleObservation.SimpleScope.class);
      final ObservationRegistry registry;
      private final Observation currentObservation;
      final Observation.@Nullable Scope previousObservationScope;

      SimpleScope(ObservationRegistry registry, Observation current) {
         this.registry = registry;
         this.currentObservation = current;
         this.previousObservationScope = registry.getCurrentObservationScope();
         this.registry.setCurrentObservationScope(this);
      }

      @Override
      public Observation getCurrentObservation() {
         return this.currentObservation;
      }

      @Override
      public void close() {
         if (this.currentObservation instanceof SimpleObservation) {
            SimpleObservation observation = (SimpleObservation)this.currentObservation;
            observation.notifyOnScopeClosed();
         } else if (this.currentObservation != null && !this.currentObservation.isNoop()) {
            log.debug("Custom observation type was used in combination with SimpleScope - that's unexpected");
         } else {
            log.trace("NoOp observation used with SimpleScope");
         }

         this.registry.setCurrentObservationScope(this.previousObservationScope);
      }

      @Deprecated
      @Override
      public void reset() {
         resetLogger.log("reset() is deprecated, will do nothing, and will be removed in a future release. Please do not use it.");
      }

      @Deprecated
      @Override
      public void makeCurrent() {
         makeCurrentLogger.log("makeCurrent() is deprecated, will do nothing, and will be removed in a future release. Please do not use it.");
      }

      @Override
      public Observation.@Nullable Scope getPreviousObservationScope() {
         return this.previousObservationScope;
      }

      @Override
      public String toString() {
         return "SimpleScope(currentObservation=" + this.currentObservation + ", previousObservationScope=" + this.previousObservationScope + ")";
      }
   }
}
