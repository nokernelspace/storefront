package org.springframework.boot.bootstrap;

import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationListener;
import org.springframework.util.Assert;

public interface BootstrapRegistry {
   <T> void register(Class<T> type, BootstrapRegistry.InstanceSupplier<T> instanceSupplier);

   <T> void registerIfAbsent(Class<T> type, BootstrapRegistry.InstanceSupplier<T> instanceSupplier);

   <T> boolean isRegistered(Class<T> type);

   <T> BootstrapRegistry.@Nullable InstanceSupplier<T> getRegisteredInstanceSupplier(Class<T> type);

   void addCloseListener(ApplicationListener<BootstrapContextClosedEvent> listener);

   @FunctionalInterface
   interface InstanceSupplier<T> {
      @Nullable T get(BootstrapContext context);

      default BootstrapRegistry.Scope getScope() {
         return BootstrapRegistry.Scope.SINGLETON;
      }

      default BootstrapRegistry.InstanceSupplier<T> withScope(BootstrapRegistry.Scope scope) {
         Assert.notNull(scope, "'scope' must not be null");
         final BootstrapRegistry.InstanceSupplier<T> parent = this;
         return new BootstrapRegistry.InstanceSupplier<T>() {
            @Override
            public @Nullable T get(BootstrapContext context) {
               return parent.get(context);
            }

            @Override
            public BootstrapRegistry.Scope getScope() {
               return scope;
            }
         };
      }

      static <T> BootstrapRegistry.InstanceSupplier<T> of(@Nullable T instance) {
         return registry -> instance;
      }

      static <T> BootstrapRegistry.InstanceSupplier<T> from(@Nullable Supplier<T> supplier) {
         return registry -> supplier != null ? supplier.get() : null;
      }
   }

   enum Scope {
      SINGLETON,
      PROTOTYPE;
   }
}
