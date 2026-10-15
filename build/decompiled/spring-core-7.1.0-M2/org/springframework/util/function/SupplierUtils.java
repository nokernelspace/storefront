package org.springframework.util.function;

import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.lang.Contract;

public abstract class SupplierUtils {
   @Contract("null -> null")
   public static <T> @Nullable T resolve(@Nullable Supplier<T> supplier) {
      return supplier != null ? supplier.get() : null;
   }

   @Contract("null -> null")
   public static @Nullable Object resolve(@Nullable Object candidate) {
      return candidate instanceof Supplier<?> supplier ? supplier.get() : candidate;
   }
}
