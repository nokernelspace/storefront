package org.springframework.boot.context.properties.bind;

import java.util.NoSuchElementException;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.lang.Contract;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

public final class BindResult<T> {
   private static final BindResult<?> UNBOUND = new BindResult(null);
   private final @Nullable T value;

   private BindResult(@Nullable T value) {
      this.value = value;
   }

   public T get() throws NoSuchElementException {
      if (this.value == null) {
         throw new NoSuchElementException("No value bound");
      } else {
         return this.value;
      }
   }

   public boolean isBound() {
      return this.value != null;
   }

   public void ifBound(Consumer<? super T> consumer) {
      Assert.notNull(consumer, "'consumer' must not be null");
      if (this.value != null) {
         consumer.accept(this.value);
      }
   }

   public <U> BindResult<U> map(Function<? super T, ? extends U> mapper) {
      Assert.notNull(mapper, "'mapper' must not be null");
      return (BindResult<U>)of(this.value != null ? mapper.apply(this.value) : null);
   }

   @Contract("!null -> !null")
   public @Nullable T orElse(@Nullable T other) {
      return this.value != null ? this.value : other;
   }

   public T orElseGet(Supplier<? extends T> other) {
      return this.value != null ? this.value : other.get();
   }

   public <X extends Throwable> T orElseThrow(Supplier<? extends X> exceptionSupplier) throws X {
      if (this.value == null) {
         throw exceptionSupplier.get();
      } else {
         return this.value;
      }
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else {
         return obj != null && this.getClass() == obj.getClass() ? ObjectUtils.nullSafeEquals(this.value, ((BindResult)obj).value) : false;
      }
   }

   @Override
   public int hashCode() {
      return ObjectUtils.nullSafeHashCode(this.value);
   }

   static <T> BindResult<T> of(@Nullable T value) {
      return (BindResult<T>)(value == null ? UNBOUND : new BindResult<>(value));
   }
}
