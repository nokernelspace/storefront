package org.springframework.boot.bootstrap;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.util.Assert;

public class DefaultBootstrapContext implements ConfigurableBootstrapContext {
   private final Map<Class<?>, BootstrapRegistry.InstanceSupplier<?>> instanceSuppliers = new HashMap<>();
   private final Map<Class<?>, Object> instances = new HashMap<>();
   private final ApplicationEventMulticaster events = new SimpleApplicationEventMulticaster();

   @Override
   public <T> void register(Class<T> type, BootstrapRegistry.InstanceSupplier<T> instanceSupplier) {
      this.register(type, instanceSupplier, true);
   }

   @Override
   public <T> void registerIfAbsent(Class<T> type, BootstrapRegistry.InstanceSupplier<T> instanceSupplier) {
      this.register(type, instanceSupplier, false);
   }

   private <T> void register(Class<T> type, BootstrapRegistry.InstanceSupplier<T> instanceSupplier, boolean replaceExisting) {
      Assert.notNull(type, "'type' must not be null");
      Assert.notNull(instanceSupplier, "'instanceSupplier' must not be null");
      synchronized (this.instanceSuppliers) {
         boolean alreadyRegistered = this.instanceSuppliers.containsKey(type);
         if (replaceExisting || !alreadyRegistered) {
            Assert.state(!this.instances.containsKey(type), () -> type.getName() + " has already been created");
            this.instanceSuppliers.put(type, instanceSupplier);
         }
      }
   }

   @Override
   public <T> boolean isRegistered(Class<T> type) {
      synchronized (this.instanceSuppliers) {
         return this.instanceSuppliers.containsKey(type);
      }
   }

   @Override
   public <T> BootstrapRegistry.@Nullable InstanceSupplier<T> getRegisteredInstanceSupplier(Class<T> type) {
      synchronized (this.instanceSuppliers) {
         return (BootstrapRegistry.InstanceSupplier<T>)this.instanceSuppliers.get(type);
      }
   }

   @Override
   public void addCloseListener(ApplicationListener<BootstrapContextClosedEvent> listener) {
      this.events.addApplicationListener(listener);
   }

   @Override
   public <T> @Nullable T get(Class<T> type) throws IllegalStateException {
      return this.getOrElseThrow(type, () -> new IllegalStateException(type.getName() + " has not been registered"));
   }

   @Override
   public <T> T getOrElse(Class<T> type, T other) {
      Supplier<T> supplier = () -> other;
      return this.getOrElseSupply(type, supplier);
   }

   @Override
   public <T> @Nullable T getOrElseSupply(Class<T> type, Supplier<@Nullable T> other) {
      synchronized (this.instanceSuppliers) {
         BootstrapRegistry.InstanceSupplier<?> instanceSupplier = this.instanceSuppliers.get(type);
         return instanceSupplier != null ? this.getInstance(type, instanceSupplier) : other.get();
      }
   }

   @Override
   public <T, X extends Throwable> @Nullable T getOrElseThrow(Class<T> type, Supplier<? extends X> exceptionSupplier) throws X {
      synchronized (this.instanceSuppliers) {
         BootstrapRegistry.InstanceSupplier<?> instanceSupplier = this.instanceSuppliers.get(type);
         if (instanceSupplier == null) {
            throw exceptionSupplier.get();
         } else {
            return this.getInstance(type, instanceSupplier);
         }
      }
   }

   private <T> @Nullable T getInstance(Class<T> type, BootstrapRegistry.InstanceSupplier<?> instanceSupplier) {
      T instance = (T)this.instances.get(type);
      if (instance == null) {
         instance = (T)instanceSupplier.get(this);
         if (instance == null) {
            return null;
         }

         if (instanceSupplier.getScope() == BootstrapRegistry.Scope.SINGLETON) {
            this.instances.put(type, instance);
         }
      }

      return instance;
   }

   public void close(ConfigurableApplicationContext applicationContext) {
      this.events.multicastEvent(new BootstrapContextClosedEvent(this, applicationContext));
   }
}
