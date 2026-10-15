package org.springframework.jmx.export.metadata;

import org.jspecify.annotations.Nullable;

public class ManagedResource extends AbstractJmxAttribute {
   private @Nullable String objectName;
   private boolean log = false;
   private @Nullable String logFile;
   private @Nullable String persistPolicy;
   private int persistPeriod = -1;
   private @Nullable String persistName;
   private @Nullable String persistLocation;

   public void setObjectName(@Nullable String objectName) {
      this.objectName = objectName;
   }

   public @Nullable String getObjectName() {
      return this.objectName;
   }

   public void setLog(boolean log) {
      this.log = log;
   }

   public boolean isLog() {
      return this.log;
   }

   public void setLogFile(@Nullable String logFile) {
      this.logFile = logFile;
   }

   public @Nullable String getLogFile() {
      return this.logFile;
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

   public void setPersistName(@Nullable String persistName) {
      this.persistName = persistName;
   }

   public @Nullable String getPersistName() {
      return this.persistName;
   }

   public void setPersistLocation(@Nullable String persistLocation) {
      this.persistLocation = persistLocation;
   }

   public @Nullable String getPersistLocation() {
      return this.persistLocation;
   }
}
