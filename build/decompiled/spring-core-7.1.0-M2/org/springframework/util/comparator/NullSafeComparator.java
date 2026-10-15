package org.springframework.util.comparator;

import java.util.Comparator;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

@Deprecated(since = "6.1")
public class NullSafeComparator<T> implements Comparator<T> {
   public static final NullSafeComparator NULLS_LOW = new NullSafeComparator(true);
   public static final NullSafeComparator NULLS_HIGH = new NullSafeComparator(false);
   private final Comparator<T> nonNullComparator;
   private final boolean nullsLow;

   private NullSafeComparator(boolean nullsLow) {
      this.nonNullComparator = Comparators.comparable();
      this.nullsLow = nullsLow;
   }

   public NullSafeComparator(Comparator<T> comparator, boolean nullsLow) {
      Assert.notNull(comparator, "Comparator must not be null");
      this.nonNullComparator = comparator;
      this.nullsLow = nullsLow;
   }

   @Override
   public int compare(@Nullable T left, @Nullable T right) {
      Comparator<T> comparator = this.nullsLow ? Comparator.nullsFirst(this.nonNullComparator) : Comparator.nullsLast(this.nonNullComparator);
      return comparator.compare(left, right);
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof NullSafeComparator<?> that && this.nonNullComparator.equals(that.nonNullComparator) && this.nullsLow == that.nullsLow;
   }

   @Override
   public int hashCode() {
      return Boolean.hashCode(this.nullsLow);
   }

   @Override
   public String toString() {
      return "NullSafeComparator: non-null comparator [" + this.nonNullComparator + "]; " + (this.nullsLow ? "nulls low" : "nulls high");
   }
}
