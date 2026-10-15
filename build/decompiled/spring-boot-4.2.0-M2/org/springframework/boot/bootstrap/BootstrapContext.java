package org.springframework.boot.bootstrap;

import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

public interface BootstrapContext {
   <T> @Nullable T get(Class<T> type) throws IllegalStateException;

   <T> @Nullable T getOrElse(Class<T> type, @Nullable T other);

   <T> @Nullable T getOrElseSupply(Class<T> type, Supplier<@Nullable T> other);

   <T, X extends Throwable> @Nullable T getOrElseThrow(Class<T> type, Supplier<? extends X> exceptionSupplier) throws X;

   <T> boolean isRegistered(Class<T> type);
}
