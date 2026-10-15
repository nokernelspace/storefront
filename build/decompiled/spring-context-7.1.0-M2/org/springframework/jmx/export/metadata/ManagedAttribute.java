package org.springframework.jmx.export.metadata;

import org.jspecify.annotations.Nullable;

public class ManagedAttribute extends AbstractJmxAttribute {
   public static final ManagedAttribute EMPTY = new ManagedAttribute();
   private @Nullable Object defaultValue;
   private @Nullable String persistPolicy;
   private int persistPeriod = -1;

   public void setDefaultValue(@Nullable Object defaultValue) {
      this.defaultValue = defaultValue;
   }

   public @Nullable Object getDefaultValue() {
      return this.defaultValue;
   }

   public void setPersistPolicy(@Nullable String persistPolicy) {
      this.persistPolicy = persistPolicy;
   }

   public @Nullable String getPersistPolicy() {
      return this.persistPolicy;
   }

   public void setPersistPeriod(int persistPeriod) {
      this.persistPeriod = persistPeriod;
   }

   public int getPersistPeriod() {
      return this.persistPeriod;
   }
}
