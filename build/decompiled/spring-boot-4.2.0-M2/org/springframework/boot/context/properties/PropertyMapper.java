package org.springframework.boot.context.properties;

import java.util.NoSuchElementException;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.util.function.SingletonSupplier;

public final class PropertyMapper {
   private static final PropertyMapper INSTANCE = new PropertyMapper(null, null);
   private final @Nullable PropertyMapper parent;
   private final PropertyMapper.@Nullable SourceOperator sourceOperator;

   private PropertyMapper(@Nullable PropertyMapper parent, PropertyMapper.@Nullable SourceOperator sourceOperator) {
      this.parent = parent;
      this.sourceOperator = sourceOperator;
   }

   public PropertyMapper alwaysApplying(PropertyMapper.SourceOperator operator) {
      Assert.notNull(operator, "'operator' must not be null");
      return new PropertyMapper(this, operator);
   }

   public <T> PropertyMapper.Source<T> from(@Nullable T value) {
      return this.from(() -> value);
   }

   public <T> PropertyMapper.Source<T> from(Supplier<? extends @Nullable T> supplier) {
      Assert.notNull(supplier, "'supplier' must not be null");
      PropertyMapper.Source<T> source = this.getSource(supplier);
      if (this.sourceOperator != null) {
         source = this.sourceOperator.apply(source);
      }

      return source;
   }

   private <T> PropertyMapper.Source<T> getSource(Supplier<? extends @Nullable T> supplier) {
      return this.parent != null ? this.parent.from(supplier) : new PropertyMapper.Source<>(SingletonSupplier.of(supplier), value -> true);
   }

   public static PropertyMapper get() {
      return INSTANCE;
   }

   public static final class Source<T> {
      private final Supplier<? extends @Nullable T> supplier;
      private final Predicate<T> predicate;

      private Source(Supplier<? extends @Nullable T> supplier, Predicate<T> predicate) {
         Assert.notNull(predicate, "'predicate' must not be null");
         this.supplier = supplier;
         this.predicate = predicate;
      }

      public PropertyMapper.Source<T> orFrom(Supplier<? extends T> fallback) {
         Assert.notNull(fallback, "'fallback' must not be null");
         Supplier<T> supplier = () -> {
            T value = this.getValue();
            return value != null ? value : fallback.get();
         };
         return new PropertyMapper.Source<>(supplier, this.predicate);
      }

      public <R extends Number> PropertyMapper.Source<Integer> asInt(PropertyMapper.Source.Adapter<? super T, ? extends R> adapter) {
         return this.as(adapter).as(Number::intValue);
      }

      public <R> PropertyMapper.Source<R> as(PropertyMapper.Source.Adapter<? super T, ? extends R> adapter) {
         Assert.notNull(adapter, "'adapter' must not be null");
         Supplier<R> supplier = () -> {
            T value = this.getValue();
            return (R)(value != null && this.predicate.test(value) ? adapter.adapt(value) : null);
         };
         Predicate<R> predicate = adaptedValue -> {
            T value = this.getValue();
            return value != null && this.predicate.test(value);
         };
         return new PropertyMapper.Source<>(supplier, predicate);
      }

      public PropertyMapper.Source<T> whenTrue() {
         return this.when(Boolean.TRUE::equals);
      }

      public PropertyMapper.Source<T> whenFalse() {
         return this.when(Boolean.FALSE::equals);
      }

      public PropertyMapper.Source<T> whenHasText() {
         return this.when(value -> StringUtils.hasText(value.toString()));
      }

      public PropertyMapper.Source<T> whenEqualTo(@Nullable Object object) {
         return this.when(value -> value.equals(object));
      }

      public <R extends T> PropertyMapper.Source<R> whenInstanceOf(Class<R> target) {
         Assert.notNull(target, "'target' must not be null");
         return this.when(target::isInstance).as(target::cast);
      }

      public PropertyMapper.Source<T> whenNot(Predicate<T> predicate) {
         Assert.notNull(predicate, "'predicate' must not be null");
         return this.when(predicate.negate());
      }

      public PropertyMapper.Source<T> when(Predicate<T> predicate) {
         Assert.notNull(predicate, "'predicate' must not be null");
         return new PropertyMapper.Source<>(this.supplier, this.predicate.and(predicate));
      }

      public void to(Consumer<? super T> consumer) {
         Assert.notNull(consumer, "'consumer' must not be null");
         T value = this.getValue();
         if (value != null && this.test(value)) {
            consumer.accept(value);
         }
      }

      public <R> R to(R instance, BiFunction<R, ? super T, R> mapper) {
         Assert.notNull(instance, "'instance' must not be null");
         Assert.notNull(mapper, "'mapper' must not be null");
         T value = this.getValue();
         return value != null && this.test(value) ? mapper.apply(instance, value) : instance;
      }

      public <R> R toInstance(Function<? super T, R> factory) {
         Assert.notNull(factory, "'factory' must not be null");
         T value = this.getValue();
         if (value != null && this.test(value)) {
            return factory.apply(value);
         } else {
            throw new NoSuchElementException("No value present");
         }
      }

      public void toCall(Runnable runnable) {
         Assert.notNull(runnable, "'runnable' must not be null");
         T value = this.getValue();
         if (value != null && this.test(value)) {
            runnable.run();
         }
      }

      public PropertyMapper.Source.Always<T> always() {
         Supplier<T> getValue = this::getValue;
         return new PropertyMapper.Source.Always<>(getValue, this::test);
      }

      private @Nullable T getValue() {
         try {
            return (T)this.supplier.get();
         } catch (NullPointerException ex) {
            return null;
         }
      }

      private boolean test(T value) {
         Assert.state(value != null, "'value' must not be null");
         return this.predicate.test(value);
      }

      @FunctionalInterface
      public interface Adapter<T, R> {
         @Nullable R adapt(T value);
      }

      public static class Always<T> {
         private final Supplier<@Nullable T> supplier;
         private final Predicate<T> predicate;

         Always(Supplier<@Nullable T> supplier, Predicate<T> predicate) {
            this.supplier = supplier;
            this.predicate = predicate;
         }

         public <R> PropertyMapper.Source.Always<R> as(PropertyMapper.Source.Always.Adapter<? super T, ? extends R> adapter) {
            Assert.notNull(adapter, "'adapter' must not be null");
            Supplier<R> supplier = () -> {
               T value = this.getValue();
               return (R)(value != null && !this.test(value) ? null : adapter.adapt(value));
            };
            Predicate<R> predicate = adaptedValue -> {
               T value = this.getValue();
               return value == null || this.test(value);
            };
            return new PropertyMapper.Source.Always<>(supplier, predicate);
         }

         public void to(Consumer<? super @Nullable T> consumer) {
            Assert.notNull(consumer, "'consumer' must not be null");
            T value = this.getValue();
            if (value == null || this.test(value)) {
               consumer.accept(value);
            }
         }

         public <R> R to(R instance, PropertyMapper.Source.Always.Mapper<R, ? super T> mapper) {
            Assert.notNull(instance, "'instance' must not be null");
            Assert.notNull(mapper, "'mapper' must not be null");
            T value = this.getValue();
            return value != null && !this.test(value) ? instance : mapper.map(instance, value);
         }

         public <R> R toInstance(PropertyMapper.Source.Always.Factory<? super T, ? extends R> factory) {
            Assert.notNull(factory, "'factory' must not be null");
            T value = this.getValue();
            if (value != null && !this.test(value)) {
               throw new NoSuchElementException("No value present");
            } else {
               return (R)factory.create(value);
            }
         }

         public void toCall(Runnable runnable) {
            Assert.notNull(runnable, "'runnable' must not be null");
            T value = this.getValue();
            if (value == null || this.test(value)) {
               runnable.run();
            }
         }

         private @Nullable T getValue() {
            return this.supplier.get();
         }

         private boolean test(T value) {
            Assert.state(value != null, "'value' must not be null");
            return this.predicate.test(value);
         }

         @FunctionalInterface
         public interface Adapter<T, R> {
            @Nullable R adapt(@Nullable T value);
         }

         @FunctionalInterface
         public interface Factory<T, R> {
            R create(@Nullable T value);
         }

         @FunctionalInterface
         public interface Mapper<R, T> {
            R map(R instance, @Nullable T value);
         }
      }
   }

   @FunctionalInterface
   public interface SourceOperator {
      <T> PropertyMapper.Source<T> apply(PropertyMapper.Source<T> source);
   }
}
