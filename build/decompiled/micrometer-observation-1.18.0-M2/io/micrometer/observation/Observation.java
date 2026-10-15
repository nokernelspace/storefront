package io.micrometer.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.common.KeyValues;
import io.micrometer.common.util.internal.logging.InternalLoggerFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public interface Observation extends ObservationView {
   Observation NOOP = new Observation() {
      @Override
      public Observation contextualName(@Nullable String contextualName) {
         return this;
      }

      @Override
      public Observation parentObservation(@Nullable Observation parentObservation) {
         return this;
      }

      @Override
      public Observation lowCardinalityKeyValue(KeyValue keyValue) {
         return this;
      }

      @Override
      public Observation lowCardinalityKeyValue(String key, String value) {
         return this;
      }

      @Override
      public Observation highCardinalityKeyValue(KeyValue keyValue) {
         return this;
      }

      @Override
      public Observation highCardinalityKeyValue(String key, String value) {
         return this;
      }

      @Override
      public Observation observationConvention(ObservationConvention<?> observationConvention) {
         return this;
      }

      @Override
      public Observation error(Throwable error) {
         return this;
      }

      @Override
      public Observation event(Observation.Event event) {
         return this;
      }

      @Override
      public Observation start() {
         return this;
      }

      @Override
      public Observation.Context getContext() {
         return NoopContext.INSTANCE;
      }

      @Override
      public void stop() {
      }

      @Override
      public Observation.Scope openScope() {
         return Observation.Scope.NOOP;
      }

      @Override
      public String toString() {
         return "Observation.NOOP";
      }
   };

   static Observation start(String name, @Nullable ObservationRegistry registry) {
      return start(name, Observation.Context::new, registry);
   }

   static <T extends Observation.Context> Observation start(String name, Supplier<T> contextSupplier, @Nullable ObservationRegistry registry) {
      return createNotStarted(name, contextSupplier, registry).start();
   }

   static Observation createNotStarted(String name, @Nullable ObservationRegistry registry) {
      return createNotStarted(name, Observation.Context::new, registry);
   }

   static <T extends Observation.Context> Observation createNotStarted(String name, Supplier<T> contextSupplier, @Nullable ObservationRegistry registry) {
      if (registry != null && !registry.isNoop()) {
         Observation.Context context = contextSupplier.get();
         context.setParentFromCurrentObservation(registry);
         return !registry.observationConfig().isObservationEnabled(name, context)
            ? NoopButScopeHandlingObservation.INSTANCE
            : new SimpleObservation(name, registry, context);
      } else {
         return NoopButScopeHandlingObservation.INSTANCE;
      }
   }

   static <T extends Observation.Context> Observation createNotStarted(
      @Nullable ObservationConvention<T> customConvention,
      ObservationConvention<T> defaultConvention,
      Supplier<T> contextSupplier,
      @Nullable ObservationRegistry registry
   ) {
      if (registry != null && !registry.isNoop()) {
         T context = (T)contextSupplier.get();
         context.setParentFromCurrentObservation(registry);
         ObservationConvention<T> convention;
         if (customConvention != null) {
            convention = customConvention;
         } else {
            convention = registry.observationConfig().getObservationConvention(context, defaultConvention);
         }

         return !registry.observationConfig().isObservationEnabled(convention.getName(), context)
            ? NoopButScopeHandlingObservation.INSTANCE
            : new SimpleObservation(convention, registry, context);
      } else {
         return NoopButScopeHandlingObservation.INSTANCE;
      }
   }

   static Observation start(ObservationConvention<Observation.Context> observationConvention, ObservationRegistry registry) {
      return start(observationConvention, Observation.Context::new, registry);
   }

   static <T extends Observation.Context> Observation start(
      ObservationConvention<T> observationConvention, Supplier<T> contextSupplier, ObservationRegistry registry
   ) {
      return createNotStarted(observationConvention, contextSupplier, registry).start();
   }

   static <T extends Observation.Context> Observation start(
      @Nullable ObservationConvention<T> customConvention,
      ObservationConvention<T> defaultConvention,
      Supplier<T> contextSupplier,
      ObservationRegistry registry
   ) {
      return createNotStarted(customConvention, defaultConvention, contextSupplier, registry).start();
   }

   static Observation createNotStarted(ObservationConvention<Observation.Context> observationConvention, ObservationRegistry registry) {
      return createNotStarted(observationConvention, Observation.Context::new, registry);
   }

   static <T extends Observation.Context> Observation createNotStarted(
      ObservationConvention<T> observationConvention, Supplier<T> contextSupplier, ObservationRegistry registry
   ) {
      if (registry != null && !registry.isNoop()) {
         T context = (T)contextSupplier.get();
         context.setParentFromCurrentObservation(registry);
         return !registry.observationConfig().isObservationEnabled(observationConvention.getName(), context)
            ? NoopButScopeHandlingObservation.INSTANCE
            : new SimpleObservation(observationConvention, registry, context);
      } else {
         return NoopButScopeHandlingObservation.INSTANCE;
      }
   }

   Observation contextualName(@Nullable String var1);

   Observation parentObservation(@Nullable Observation var1);

   Observation lowCardinalityKeyValue(KeyValue var1);

   default Observation lowCardinalityKeyValue(String key, String value) {
      return this.lowCardinalityKeyValue(KeyValue.of(key, value));
   }

   default Observation lowCardinalityKeyValues(KeyValues keyValues) {
      for (KeyValue keyValue : keyValues) {
         this.lowCardinalityKeyValue(keyValue);
      }

      return this;
   }

   Observation highCardinalityKeyValue(KeyValue var1);

   default Observation highCardinalityKeyValue(String key, String value) {
      return this.highCardinalityKeyValue(KeyValue.of(key, value));
   }

   default Observation highCardinalityKeyValues(KeyValues keyValues) {
      for (KeyValue keyValue : keyValues) {
         this.highCardinalityKeyValue(keyValue);
      }

      return this;
   }

   default boolean isNoop() {
      return this == NOOP || this == NoopButScopeHandlingObservation.INSTANCE;
   }

   Observation observationConvention(ObservationConvention<?> var1);

   Observation error(Throwable var1);

   Observation event(Observation.Event var1);

   Observation start();

   Observation.Context getContext();

   @Override
   default Observation.ContextView getContextView() {
      return this.getContext();
   }

   void stop();

   Observation.Scope openScope();

   default void observe(Runnable runnable) {
      this.start();

      try {
         Observation.Scope scope = this.openScope();

         try {
            runnable.run();
         } catch (Throwable var11) {
            if (scope != null) {
               try {
                  scope.close();
               } catch (Throwable var10) {
                  var11.addSuppressed(var10);
               }
            }

            throw var11;
         }

         if (scope != null) {
            scope.close();
         }
      } catch (Throwable error) {
         this.error(error);
         throw error;
      } finally {
         this.stop();
      }
   }

   default Runnable wrap(Runnable runnable) {
      return () -> this.observe(runnable);
   }

   default <E extends Throwable> void observeChecked(Observation.CheckedRunnable<E> checkedRunnable) throws E {
      this.start();

      try {
         Observation.Scope scope = this.openScope();

         try {
            checkedRunnable.run();
         } catch (Throwable var11) {
            if (scope != null) {
               try {
                  scope.close();
               } catch (Throwable var10) {
                  var11.addSuppressed(var10);
               }
            }

            throw var11;
         }

         if (scope != null) {
            scope.close();
         }
      } catch (Throwable error) {
         this.error(error);
         throw error;
      } finally {
         this.stop();
      }
   }

   default <E extends Throwable> Observation.CheckedRunnable<E> wrapChecked(Observation.CheckedRunnable<E> checkedRunnable) throws E {
      return () -> this.observeChecked(checkedRunnable);
   }

   default <T> T observe(Supplier<T> supplier) {
      this.start();

      Object var3;
      try {
         Observation.Scope scope = this.openScope();

         try {
            var3 = supplier.get();
         } catch (Throwable var11) {
            if (scope != null) {
               try {
                  scope.close();
               } catch (Throwable var10) {
                  var11.addSuppressed(var10);
               }
            }

            throw var11;
         }

         if (scope != null) {
            scope.close();
         }
      } catch (Throwable error) {
         this.error(error);
         throw error;
      } finally {
         this.stop();
      }

      return (T)var3;
   }

   default <T> Supplier<T> wrap(Supplier<T> supplier) {
      return () -> this.observe(supplier);
   }

   default <T, E extends Throwable> T observeChecked(Observation.CheckedCallable<T, E> checkedCallable) throws E {
      this.start();

      Object var3;
      try {
         Observation.Scope scope = this.openScope();

         try {
            var3 = checkedCallable.call();
         } catch (Throwable var11) {
            if (scope != null) {
               try {
                  scope.close();
               } catch (Throwable var10) {
                  var11.addSuppressed(var10);
               }
            }

            throw var11;
         }

         if (scope != null) {
            scope.close();
         }
      } catch (Throwable error) {
         this.error(error);
         throw error;
      } finally {
         this.stop();
      }

      return (T)var3;
   }

   default <T, E extends Throwable> Observation.CheckedCallable<T, E> wrapChecked(Observation.CheckedCallable<T, E> checkedCallable) throws E {
      return () -> this.observeChecked(checkedCallable);
   }

   @Deprecated
   default <C extends Observation.Context, T> T observeWithContext(Function<C, T> function) {
      InternalLoggerFactory.getInstance(Observation.class).warn("This method is deprecated. Please migrate to observation.observe(...)");
      this.start();

      Object var3;
      try {
         Observation.Scope scope = this.openScope();

         try {
            var3 = function.apply((C)this.getContext());
         } catch (Throwable var11) {
            if (scope != null) {
               try {
                  scope.close();
               } catch (Throwable var10) {
                  var11.addSuppressed(var10);
               }
            }

            throw var11;
         }

         if (scope != null) {
            scope.close();
         }
      } catch (Throwable error) {
         this.error(error);
         throw error;
      } finally {
         this.stop();
      }

      return (T)var3;
   }

   @Deprecated
   default <C extends Observation.Context, T, E extends Throwable> T observeCheckedWithContext(Observation.CheckedFunction<C, T, E> function) throws E {
      InternalLoggerFactory.getInstance(Observation.class).warn("This method is deprecated. Please migrate to observation.observeChecked(...)");
      this.start();

      Object var3;
      try {
         Observation.Scope scope = this.openScope();

         try {
            var3 = function.apply((C)this.getContext());
         } catch (Throwable var11) {
            if (scope != null) {
               try {
                  scope.close();
               } catch (Throwable var10) {
                  var11.addSuppressed(var10);
               }
            }

            throw var11;
         }

         if (scope != null) {
            scope.close();
         }
      } catch (Throwable error) {
         this.error(error);
         throw error;
      } finally {
         this.stop();
      }

      return (T)var3;
   }

   default void scoped(Runnable runnable) {
      try {
         Observation.Scope scope = this.openScope();

         try {
            runnable.run();
         } catch (Throwable var6) {
            if (scope != null) {
               try {
                  scope.close();
               } catch (Throwable var5) {
                  var6.addSuppressed(var5);
               }
            }

            throw var6;
         }

         if (scope != null) {
            scope.close();
         }
      } catch (Exception exception) {
         this.error(exception);
         throw exception;
      }
   }

   default <E extends Throwable> void scopedChecked(Observation.CheckedRunnable<E> checkedRunnable) throws E {
      try {
         Observation.Scope scope = this.openScope();

         try {
            checkedRunnable.run();
         } catch (Throwable var6) {
            if (scope != null) {
               try {
                  scope.close();
               } catch (Throwable var5) {
                  var6.addSuppressed(var5);
               }
            }

            throw var6;
         }

         if (scope != null) {
            scope.close();
         }
      } catch (Throwable throwable) {
         this.error(throwable);
         throw throwable;
      }
   }

   default <T> T scoped(Supplier<T> supplier) {
      try {
         Observation.Scope scope = this.openScope();

         Object var3;
         try {
            var3 = supplier.get();
         } catch (Throwable var6) {
            if (scope != null) {
               try {
                  scope.close();
               } catch (Throwable var5) {
                  var6.addSuppressed(var5);
               }
            }

            throw var6;
         }

         if (scope != null) {
            scope.close();
         }

         return (T)var3;
      } catch (Exception exception) {
         this.error(exception);
         throw exception;
      }
   }

   default <T, E extends Throwable> T scopedChecked(Observation.CheckedCallable<T, E> checkedCallable) throws E {
      try {
         Observation.Scope scope = this.openScope();

         Object var3;
         try {
            var3 = checkedCallable.call();
         } catch (Throwable var6) {
            if (scope != null) {
               try {
                  scope.close();
               } catch (Throwable var5) {
                  var6.addSuppressed(var5);
               }
            }

            throw var6;
         }

         if (scope != null) {
            scope.close();
         }

         return (T)var3;
      } catch (Throwable error) {
         this.error(error);
         throw error;
      }
   }

   static void tryScoped(@Nullable Observation parent, Runnable action) {
      if (parent != null) {
         parent.scoped(action);
      } else {
         action.run();
      }
   }

   static <E extends Throwable> void tryScopedChecked(@Nullable Observation parent, Observation.CheckedRunnable<E> checkedRunnable) throws E {
      if (parent != null) {
         parent.scopedChecked(checkedRunnable);
      } else {
         checkedRunnable.run();
      }
   }

   static <T> T tryScoped(@Nullable Observation parent, Supplier<T> action) {
      return parent != null ? parent.scoped(action) : action.get();
   }

   static <T, E extends Throwable> T tryScopedChecked(@Nullable Observation parent, Observation.CheckedCallable<T, E> checkedCallable) throws E {
      return parent != null ? parent.scopedChecked(checkedCallable) : checkedCallable.call();
   }

   @FunctionalInterface
   interface CheckedCallable<T, E extends Throwable> {
      T call() throws E;
   }

   @FunctionalInterface
   interface CheckedFunction<T, R, E extends Throwable> {
      R apply(T var1) throws E;
   }

   @FunctionalInterface
   interface CheckedRunnable<E extends Throwable> {
      void run() throws E;
   }

   class Context implements Observation.ContextView {
      private final Map<Object, Object> map = new ConcurrentHashMap<>();
      private @Nullable String name;
      private @Nullable String contextualName;
      private @Nullable Throwable error;
      private @Nullable ObservationView parentObservation;
      private final Map<String, KeyValue> lowCardinalityKeyValues = new ConcurrentHashMap<>();
      private final Map<String, KeyValue> highCardinalityKeyValues = new ConcurrentHashMap<>();

      @Override
      public @Nullable String getName() {
         return this.name;
      }

      public void setName(String name) {
         this.name = name;
      }

      @Override
      public @Nullable String getContextualName() {
         return this.contextualName;
      }

      public void setContextualName(@Nullable String contextualName) {
         this.contextualName = contextualName;
      }

      @Override
      public @Nullable ObservationView getParentObservation() {
         return this.parentObservation;
      }

      public void setParentObservation(@Nullable ObservationView parentObservation) {
         this.parentObservation = parentObservation;
      }

      void setParentFromCurrentObservation(ObservationRegistry registry) {
         if (this.parentObservation == null) {
            Observation currentObservation = registry.getCurrentObservation();
            if (currentObservation != null) {
               this.setParentObservation(currentObservation);
            }
         }
      }

      @Override
      public @Nullable Throwable getError() {
         return this.error;
      }

      public void setError(Throwable error) {
         this.error = error;
      }

      public <T> Observation.Context put(Object key, T object) {
         this.map.put(key, object);
         return this;
      }

      @Override
      public <T> @Nullable T get(Object key) {
         return (T)this.map.get(key);
      }

      public @Nullable Object remove(Object key) {
         return this.map.remove(key);
      }

      @Override
      public <T> T getRequired(Object key) {
         T object = (T)this.map.get(key);
         if (object == null) {
            throw new IllegalArgumentException("Context does not have an entry for key [" + key + "]");
         } else {
            return object;
         }
      }

      @Override
      public boolean containsKey(Object key) {
         return this.map.containsKey(key);
      }

      @Override
      public <T> T getOrDefault(Object key, T defaultObject) {
         return (T)this.map.getOrDefault(key, defaultObject);
      }

      public <T> T computeIfAbsent(Object key, Function<Object, ? extends T> mappingFunction) {
         return (T)this.map.computeIfAbsent(key, mappingFunction);
      }

      public void clear() {
         this.map.clear();
      }

      public Observation.Context addLowCardinalityKeyValue(KeyValue keyValue) {
         this.lowCardinalityKeyValues.put(keyValue.getKey(), keyValue);
         return this;
      }

      public Observation.Context addHighCardinalityKeyValue(KeyValue keyValue) {
         this.highCardinalityKeyValues.put(keyValue.getKey(), keyValue);
         return this;
      }

      public Observation.Context removeLowCardinalityKeyValue(String keyName) {
         this.lowCardinalityKeyValues.remove(keyName);
         return this;
      }

      public Observation.Context removeHighCardinalityKeyValue(String keyName) {
         this.highCardinalityKeyValues.remove(keyName);
         return this;
      }

      public Observation.Context addLowCardinalityKeyValues(KeyValues keyValues) {
         for (KeyValue keyValue : keyValues) {
            this.addLowCardinalityKeyValue(keyValue);
         }

         return this;
      }

      public Observation.Context addHighCardinalityKeyValues(KeyValues keyValues) {
         for (KeyValue keyValue : keyValues) {
            this.addHighCardinalityKeyValue(keyValue);
         }

         return this;
      }

      public Observation.Context removeLowCardinalityKeyValues(String... keyNames) {
         for (String keyName : keyNames) {
            this.removeLowCardinalityKeyValue(keyName);
         }

         return this;
      }

      public Observation.Context removeHighCardinalityKeyValues(String... keyNames) {
         for (String keyName : keyNames) {
            this.removeHighCardinalityKeyValue(keyName);
         }

         return this;
      }

      @Override
      public KeyValues getLowCardinalityKeyValues() {
         return KeyValues.of(this.lowCardinalityKeyValues.values());
      }

      @Override
      public KeyValues getHighCardinalityKeyValues() {
         return KeyValues.of(this.highCardinalityKeyValues.values());
      }

      @Override
      public @Nullable KeyValue getLowCardinalityKeyValue(String key) {
         return this.lowCardinalityKeyValues.get(key);
      }

      @Override
      public @Nullable KeyValue getHighCardinalityKeyValue(String key) {
         return this.highCardinalityKeyValues.get(key);
      }

      @Override
      public KeyValues getAllKeyValues() {
         return this.getLowCardinalityKeyValues().and(this.getHighCardinalityKeyValues());
      }

      @Override
      public String toString() {
         return "name='"
            + this.name
            + '\''
            + ", contextualName='"
            + this.contextualName
            + '\''
            + ", error='"
            + this.error
            + '\''
            + ", lowCardinalityKeyValues="
            + this.toString(this.getLowCardinalityKeyValues())
            + ", highCardinalityKeyValues="
            + this.toString(this.getHighCardinalityKeyValues())
            + ", map="
            + this.toString(this.map)
            + ", parentObservation="
            + this.parentObservation;
      }

      private String toString(KeyValues keyValues) {
         return keyValues.stream()
            .map(keyValue -> String.format("%s='%s'", keyValue.getKey(), keyValue.getValue()))
            .collect(Collectors.joining(", ", "[", "]"));
      }

      private String toString(Map<Object, Object> map) {
         return map.entrySet().stream().map(entry -> String.format("%s='%s'", entry.getKey(), entry.getValue())).collect(Collectors.joining(", ", "[", "]"));
      }
   }

   interface ContextView {
      @Nullable String getName();

      @Nullable String getContextualName();

      @Nullable ObservationView getParentObservation();

      @Nullable Throwable getError();

      <T> @Nullable T get(Object var1);

      <T> T getRequired(Object var1);

      boolean containsKey(Object var1);

      <T> T getOrDefault(Object var1, T var2);

      default <T> T getOrDefault(Object key, Supplier<T> defaultObjectSupplier) {
         T value = this.get(key);
         return value != null ? value : defaultObjectSupplier.get();
      }

      KeyValues getLowCardinalityKeyValues();

      KeyValues getHighCardinalityKeyValues();

      @Nullable KeyValue getLowCardinalityKeyValue(String var1);

      @Nullable KeyValue getHighCardinalityKeyValue(String var1);

      KeyValues getAllKeyValues();
   }

   interface Event {
      static Observation.Event of(String name, String contextualName) {
         return new SimpleEvent(name, contextualName);
      }

      static Observation.Event of(String name, String contextualName, long wallTime) {
         return of(name, contextualName, wallTime, KeyValues.empty());
      }

      static Observation.Event of(String name, String contextualName, long wallTime, Iterable<KeyValue> highCardinalityKeyValues) {
         return of(name, contextualName, wallTime, highCardinalityKeyValues, KeyValues.empty());
      }

      static Observation.Event of(
         String name, String contextualName, long wallTime, Iterable<KeyValue> highCardinalityKeyValues, Iterable<KeyValue> lowCardinalityKeyValues
      ) {
         return new SimpleEvent(name, contextualName, wallTime, highCardinalityKeyValues, lowCardinalityKeyValues);
      }

      static Observation.Event of(String name) {
         return of(name, name);
      }

      String getName();

      default long getWallTime() {
         return 0L;
      }

      default String getContextualName() {
         return this.getName();
      }

      default KeyValues getLowCardinalityKeyValues() {
         return KeyValues.empty();
      }

      default KeyValues getHighCardinalityKeyValues() {
         return KeyValues.empty();
      }

      default KeyValues getKeyValues() {
         return this.getLowCardinalityKeyValues().and(this.getHighCardinalityKeyValues());
      }

      default Observation.Event format(Object... dynamicEntriesForContextualName) {
         return of(
            this.getName(),
            String.format(this.getContextualName(), dynamicEntriesForContextualName),
            this.getWallTime(),
            this.getHighCardinalityKeyValues(),
            this.getLowCardinalityKeyValues()
         );
      }
   }

   interface Scope extends AutoCloseable {
      Observation.Scope NOOP = new Observation.Scope() {
         @Override
         public Observation getCurrentObservation() {
            return Observation.NOOP;
         }

         @Override
         public void close() {
         }

         @Deprecated
         @Override
         public void reset() {
         }

         @Deprecated
         @Override
         public void makeCurrent() {
         }

         @Override
         public String toString() {
            return "Scope.NOOP";
         }
      };

      Observation getCurrentObservation();

      default Observation.@Nullable Scope getPreviousObservationScope() {
         return null;
      }

      @Override
      void close();

      @Deprecated
      void reset();

      @Deprecated
      void makeCurrent();

      default boolean isNoop() {
         return this == NOOP;
      }
   }
}
