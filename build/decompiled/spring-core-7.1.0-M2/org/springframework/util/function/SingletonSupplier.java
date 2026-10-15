package org.springframework.util.function;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.lang.Contract;
import org.springframework.util.Assert;

public class SingletonSupplier<T> implements Supplier<T> {
   private final @Nullable Supplier<? extends @Nullable T> instanceSupplier;
   private final @Nullable Supplier<? extends @Nullable T> defaultSupplier;
   private volatile @Nullable T singletonInstance;
   private boolean initialized;
   private final Lock initializationLock = new ReentrantLock();

   public SingletonSupplier(@Nullable T instance, Supplier<? extends @Nullable T> defaultSupplier) {
      this.instanceSupplier = null;
      this.defaultSupplier = defaultSupplier;
      this.singletonInstance = instance;
      this.initialized = instance != null;
   }

   public SingletonSupplier(@Nullable Supplier<? extends @Nullable T> instanceSupplier, Supplier<? extends @Nullable T> defaultSupplier) {
      this.instanceSupplier = instanceSupplier;
      this.defaultSupplier = defaultSupplier;
   }

   private SingletonSupplier(Supplier<? extends @Nullable T> supplier) {
      this.instanceSupplier = supplier;
      this.defaultSupplier = null;
   }

   private SingletonSupplier(@Nullable T singletonInstance) {
      this.instanceSupplier = null;
      this.defaultSupplier = null;
      this.singletonInstance = singletonInstance;
      this.initialized = singletonInstance != null;
   }

   @Override
   public @Nullable T get() {
      T instance = this.singletonInstance;
      if (instance == null) {
         this.initializationLock.lock();

         try {
            instance = this.singletonInstance;
            if (!this.initialized) {
               if (this.instanceSupplier != null) {
                  instance = (T)this.instanceSupplier.get();
               }

               if (instance == null && this.defaultSupplier != null) {
                  instance = (T)this.defaultSupplier.get();
               }

               this.singletonInstance = instance;
               this.initialized = true;
            }
         } finally {
            this.initializationLock.unlock();
         }
      }

      return instance;
   }

   public T obtain() {
      T instance = this.get();
      Assert.state(instance != null, "No instance from Supplier");
      return instance;
   }

   public static <T> SingletonSupplier<T> of(T instance) {
      return new SingletonSupplier<>(instance);
   }

   @Contract("null -> null; !null -> !null")
   public static <T> @Nullable SingletonSupplier<T> ofNullable(@Nullable T instance) {
      return instance != null ? new SingletonSupplier<>(instance) : null;
   }

   public static <T> SingletonSupplier<T> of(Supplier<T> supplier) {
      return new SingletonSupplier<>(supplier);
   }

   @Contract("null -> null; !null -> !null")
   public static <T> @Nullable SingletonSupplier<T> ofNullable(@Nullable Supplier<T> supplier) {
      return supplier != null ? new SingletonSupplier<>(supplier) : null;
   }
}
