package org.springframework.scheduling.support;

import java.time.temporal.Temporal;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

final class CompositeCronField extends CronField {
   private final CronField[] fields;
   private final String value;

   private CompositeCronField(CronField.Type type, CronField[] fields, String value) {
      super(type);
      this.fields = fields;
      this.value = value;
   }

   public static CronField compose(CronField[] fields, CronField.Type type, String value) {
      Assert.notEmpty(fields, "Fields must not be empty");
      Assert.hasLength(value, "Value must not be empty");
      return fields.length == 1 ? fields[0] : new CompositeCronField(type, fields, value);
   }

   @Override
   public <T extends Temporal & Comparable<? super T>> @Nullable T nextOrSame(T temporal) {
      T result = null;

      for (CronField field : this.fields) {
         T candidate = field.nextOrSame(temporal);
         if (result == null || candidate != null && candidate.compareTo(result) < 0) {
            result = candidate;
         }
      }

      return result;
   }

   @Override
   public int hashCode() {
      return this.value.hashCode();
   }

   @Override
   public boolean equals(@Nullable Object o) {
      if (this == o) {
         return true;
      } else {
         return !(o instanceof CompositeCronField other) ? false : this.type() == other.type() && this.value.equals(other.value);
      }
   }

   @Override
   public String toString() {
      return this.type() + " '" + this.value + "'";
   }
}
