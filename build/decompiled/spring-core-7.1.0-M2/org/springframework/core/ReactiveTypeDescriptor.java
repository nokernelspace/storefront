package org.springframework.core;

import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public final class ReactiveTypeDescriptor {
   private final Class<?> reactiveType;
   private final boolean multiValue;
   private final boolean noValue;
   private final @Nullable Supplier<?> emptySupplier;
   private final boolean deferred;

   private ReactiveTypeDescriptor(Class<?> reactiveType, boolean multiValue, boolean noValue, @Nullable Supplier<?> emptySupplier) {
      this(reactiveType, multiValue, noValue, emptySupplier, true);
   }

   private ReactiveTypeDescriptor(Class<?> reactiveType, boolean multiValue, boolean noValue, @Nullable Supplier<?> emptySupplier, boolean deferred) {
      Assert.notNull(reactiveType, "'reactiveType' must not be null");
      this.reactiveType = reactiveType;
      this.multiValue = multiValue;
      this.noValue = noValue;
      this.emptySupplier = emptySupplier;
      this.deferred = deferred;
   }

   public Class<?> getReactiveType() {
      return this.reactiveType;
   }

   public boolean isMultiValue() {
      return this.multiValue;
   }

   public boolean isNoValue() {
      return this.noValue;
   }

   public boolean supportsEmpty() {
      return this.emptySupplier != null;
   }

   public Object getEmptyValue() {
      Assert.state(this.emptySupplier != null, "Empty values not supported");
      Object emptyValue = this.emptySupplier.get();
      Assert.notNull(emptyValue, "Invalid null return value from emptySupplier");
      return emptyValue;
   }

   public boolean isDeferred() {
      return this.deferred;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      if (this == other) {
         return true;
      } else {
         return other != null && this.getClass() == other.getClass() ? this.reactiveType.equals(((ReactiveTypeDescriptor)other).reactiveType) : false;
      }
   }

   @Override
   public int hashCode() {
      return this.reactiveType.hashCode();
   }

   public static ReactiveTypeDescriptor multiValue(Class<?> type, Supplier<?> emptySupplier) {
      return new ReactiveTypeDescriptor(type, true, false, emptySupplier);
   }

   public static ReactiveTypeDescriptor singleOptionalValue(Class<?> type, Supplier<?> emptySupplier) {
      return new ReactiveTypeDescriptor(type, false, false, emptySupplier);
   }

   public static ReactiveTypeDescriptor singleRequiredValue(Class<?> type) {
      return new ReactiveTypeDescriptor(type, false, false, null);
   }

   public static ReactiveTypeDescriptor noValue(Class<?> type, Supplier<?> emptySupplier) {
      return new ReactiveTypeDescriptor(type, false, true, emptySupplier);
   }

   public static ReactiveTypeDescriptor nonDeferredAsyncValue(Class<?> type, Supplier<?> emptySupplier) {
      return new ReactiveTypeDescriptor(type, false, false, emptySupplier, false);
   }
}
