package org.springframework.util.comparator;

import java.io.Serializable;
import java.util.Comparator;
import org.jspecify.annotations.Nullable;

public class BooleanComparator implements Comparator<Boolean>, Serializable {
   public static final BooleanComparator TRUE_LOW = new BooleanComparator(true);
   public static final BooleanComparator TRUE_HIGH = new BooleanComparator(false);
   private final boolean trueLow;

   public BooleanComparator(boolean trueLow) {
      this.trueLow = trueLow;
   }

   public int compare(Boolean left, Boolean right) {
      int multiplier = this.trueLow ? -1 : 1;
      return multiplier * Boolean.compare(left, right);
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof BooleanComparator that && this.trueLow == that.trueLow;
   }

   @Override
   public int hashCode() {
      return Boolean.hashCode(this.trueLow);
   }

   @Override
   public String toString() {
      return "BooleanComparator: " + (this.trueLow ? "true low" : "true high");
   }
}
