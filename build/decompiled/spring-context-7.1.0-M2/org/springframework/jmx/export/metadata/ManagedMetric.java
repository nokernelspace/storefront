package org.springframework.jmx.export.metadata;

import org.jspecify.annotations.Nullable;
import org.springframework.jmx.support.MetricType;
import org.springframework.util.Assert;

public class ManagedMetric extends AbstractJmxAttribute {
   private @Nullable String category;
   private @Nullable String displayName;
   private MetricType metricType = MetricType.GAUGE;
   private int persistPeriod = -1;
   private @Nullable String persistPolicy;
   private @Nullable String unit;

   public void setCategory(@Nullable String category) {
      this.category = category;
   }

   public @Nullable String getCategory() {
      return this.category;
   }

   public void setDisplayName(@Nullable String displayName) {
      this.displayName = displayName;
   }

   public @Nullable String getDisplayName() {
      return this.displayName;
   }

   public void setMetricType(MetricType metricType) {
      Assert.notNull(metricType, "MetricType must not be null");
      this.metricType = metricType;
   }

   public MetricType getMetricType() {
      return this.metricType;
   }

   public void setPersistPeriod(int persistPeriod) {
      this.persistPeriod = persistPeriod;
   }

   public int getPersistPeriod() {
      return this.persistPeriod;
   }

   public void setPersistPolicy(@Nullable String persistPolicy) {
      this.persistPolicy = persistPolicy;
   }

   public @Nullable String getPersistPolicy() {
      return this.persistPolicy;
   }

   public void setUnit(@Nullable String unit) {
      this.unit = unit;
   }

   public @Nullable String getUnit() {
      return this.unit;
   }
}
