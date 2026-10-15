package org.springframework.boot.context.properties.bind;

import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.boot.context.properties.source.ConfigurationPropertySource;

abstract class AggregateBinder<T> {
   private final Binder.Context context;

   AggregateBinder(Binder.Context context) {
      this.context = context;
   }

   protected abstract boolean isAllowRecursiveBinding(@Nullable ConfigurationPropertySource source);

   final @Nullable Object bind(ConfigurationPropertyName name, Bindable<?> target, AggregateElementBinder elementBinder) {
      Object result = this.bindAggregate(name, target, elementBinder);
      Supplier<?> value = target.getValue();
      return result != null && value != null ? this.merge((Supplier<T>)value, (T)result) : result;
   }

   protected abstract @Nullable Object bindAggregate(ConfigurationPropertyName name, Bindable<?> target, AggregateElementBinder elementBinder);

   protected abstract T merge(Supplier<T> existing, T additional);

   protected final Binder.Context getContext() {
      return this.context;
   }

   protected static class AggregateSupplier<T> {
      private final Supplier<T> supplier;
      private @Nullable T supplied;

      public AggregateSupplier(Supplier<T> supplier) {
         this.supplier = supplier;
      }

      public T get() {
         if (this.supplied == null) {
            this.supplied = this.supplier.get();
         }

         return this.supplied;
      }

      public boolean wasSupplied() {
         return this.supplied != null;
      }
   }
}
