package org.springframework.boot.logging;

import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

public final class LoggerConfiguration {
   private final String name;
   private final LoggerConfiguration.@Nullable LevelConfiguration levelConfiguration;
   private final LoggerConfiguration.LevelConfiguration inheritedLevelConfiguration;

   public LoggerConfiguration(String name, @Nullable LogLevel configuredLevel, LogLevel effectiveLevel) {
      Assert.notNull(name, "'name' must not be null");
      Assert.notNull(effectiveLevel, "'effectiveLevel' must not be null");
      this.name = name;
      this.levelConfiguration = configuredLevel != null ? LoggerConfiguration.LevelConfiguration.of(configuredLevel) : null;
      this.inheritedLevelConfiguration = LoggerConfiguration.LevelConfiguration.of(effectiveLevel);
   }

   public LoggerConfiguration(
      String name, LoggerConfiguration.@Nullable LevelConfiguration levelConfiguration, LoggerConfiguration.LevelConfiguration inheritedLevelConfiguration
   ) {
      Assert.notNull(name, "'name' must not be null");
      Assert.notNull(inheritedLevelConfiguration, "'inheritedLevelConfiguration' must not be null");
      this.name = name;
      this.levelConfiguration = levelConfiguration;
      this.inheritedLevelConfiguration = inheritedLevelConfiguration;
   }

   public String getName() {
      return this.name;
   }

   public @Nullable LogLevel getConfiguredLevel() {
      LoggerConfiguration.LevelConfiguration configuration = this.getLevelConfiguration(LoggerConfiguration.ConfigurationScope.DIRECT);
      return configuration != null ? configuration.getLevel() : null;
   }

   public LogLevel getEffectiveLevel() {
      return this.getLevelConfiguration().getLevel();
   }

   public LoggerConfiguration.LevelConfiguration getLevelConfiguration() {
      LoggerConfiguration.LevelConfiguration result = this.getLevelConfiguration(LoggerConfiguration.ConfigurationScope.INHERITED);
      Assert.state(result != null, "Inherited level configuration must not be null");
      return result;
   }

   public LoggerConfiguration.@Nullable LevelConfiguration getLevelConfiguration(LoggerConfiguration.ConfigurationScope scope) {
      return scope != LoggerConfiguration.ConfigurationScope.DIRECT ? this.inheritedLevelConfiguration : this.levelConfiguration;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else if (obj != null && this.getClass() == obj.getClass()) {
         LoggerConfiguration other = (LoggerConfiguration)obj;
         return ObjectUtils.nullSafeEquals(this.name, other.name)
            && ObjectUtils.nullSafeEquals(this.levelConfiguration, other.levelConfiguration)
            && ObjectUtils.nullSafeEquals(this.inheritedLevelConfiguration, other.inheritedLevelConfiguration);
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.name, this.levelConfiguration, this.inheritedLevelConfiguration);
   }

   @Override
   public String toString() {
      return "LoggerConfiguration [name="
         + this.name
         + ", levelConfiguration="
         + this.levelConfiguration
         + ", inheritedLevelConfiguration="
         + this.inheritedLevelConfiguration
         + "]";
   }

   public enum ConfigurationScope {
      DIRECT,
      INHERITED;
   }

   public static final class LevelConfiguration {
      private final String name;
      private final @Nullable LogLevel logLevel;

      private LevelConfiguration(String name, @Nullable LogLevel logLevel) {
         this.name = name;
         this.logLevel = logLevel;
      }

      public String getName() {
         return this.name;
      }

      public LogLevel getLevel() {
         Assert.state(this.logLevel != null, () -> "Unable to provide LogLevel for '" + this.name + "'");
         return this.logLevel;
      }

      public boolean isCustom() {
         return this.logLevel == null;
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         } else if (obj != null && this.getClass() == obj.getClass()) {
            LoggerConfiguration.LevelConfiguration other = (LoggerConfiguration.LevelConfiguration)obj;
            return this.logLevel == other.logLevel && ObjectUtils.nullSafeEquals(this.name, other.name);
         } else {
            return false;
         }
      }

      @Override
      public int hashCode() {
         return Objects.hash(this.logLevel, this.name);
      }

      @Override
      public String toString() {
         return "LevelConfiguration [name=" + this.name + ", logLevel=" + this.logLevel + "]";
      }

      public static LoggerConfiguration.LevelConfiguration of(LogLevel logLevel) {
         Assert.notNull(logLevel, "'logLevel' must not be null");
         return new LoggerConfiguration.LevelConfiguration(logLevel.name(), logLevel);
      }

      public static LoggerConfiguration.LevelConfiguration ofCustom(String name) {
         Assert.hasText(name, "'name' must not be empty");
         return new LoggerConfiguration.LevelConfiguration(name, null);
      }
   }
}
