package org.springframework.boot.logging;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.SystemPropertyUtils;

public abstract class AbstractLoggingSystem extends LoggingSystem {
   protected static final Comparator<LoggerConfiguration> CONFIGURATION_COMPARATOR = new LoggerConfigurationComparator("ROOT");
   private final ClassLoader classLoader;

   public AbstractLoggingSystem(ClassLoader classLoader) {
      this.classLoader = classLoader;
   }

   @Override
   public void beforeInitialize() {
   }

   @Override
   public void initialize(LoggingInitializationContext initializationContext, @Nullable String configLocation, @Nullable LogFile logFile) {
      if (StringUtils.hasLength(configLocation)) {
         this.initializeWithSpecificConfig(initializationContext, configLocation, logFile);
      } else {
         this.initializeWithConventions(initializationContext, logFile);
      }
   }

   private void initializeWithSpecificConfig(LoggingInitializationContext initializationContext, String configLocation, @Nullable LogFile logFile) {
      configLocation = SystemPropertyUtils.resolvePlaceholders(configLocation);
      this.loadConfiguration(initializationContext, configLocation, logFile);
   }

   private void initializeWithConventions(LoggingInitializationContext initializationContext, @Nullable LogFile logFile) {
      String config = this.getSelfInitializationConfig();
      if (config != null && logFile == null) {
         this.reinitialize(initializationContext);
      } else {
         if (config == null) {
            config = this.getSpringInitializationConfig();
         }

         if (config != null) {
            this.loadConfiguration(initializationContext, config, logFile);
         } else {
            this.loadDefaults(initializationContext, logFile);
         }
      }
   }

   protected @Nullable String getSelfInitializationConfig() {
      return this.findConfig(this.getStandardConfigLocations());
   }

   protected @Nullable String getSpringInitializationConfig() {
      return this.findConfig(this.getSpringConfigLocations());
   }

   private @Nullable String findConfig(String[] locations) {
      for (String location : locations) {
         ClassPathResource resource = new ClassPathResource(location, this.classLoader);
         if (resource.exists()) {
            return "classpath:" + location;
         }
      }

      return null;
   }

   protected abstract String[] getStandardConfigLocations();

   protected String[] getSpringConfigLocations() {
      String[] locations = this.getStandardConfigLocations();

      for (int i = 0; i < locations.length; i++) {
         String extension = StringUtils.getFilenameExtension(locations[i]);
         int extensionLength = extension != null ? extension.length() + 1 : 0;
         locations[i] = locations[i].substring(0, locations[i].length() - extensionLength) + "-spring." + extension;
      }

      return locations;
   }

   protected abstract void loadDefaults(LoggingInitializationContext initializationContext, @Nullable LogFile logFile);

   protected abstract void loadConfiguration(LoggingInitializationContext initializationContext, String location, @Nullable LogFile logFile);

   protected void reinitialize(LoggingInitializationContext initializationContext) {
   }

   protected final ClassLoader getClassLoader() {
      return this.classLoader;
   }

   protected final String getPackagedConfigFile(String fileName) {
      String defaultPath = ClassUtils.getPackageName(this.getClass());
      defaultPath = defaultPath.replace('.', '/');
      defaultPath = defaultPath + "/" + fileName;
      return "classpath:" + defaultPath;
   }

   protected final void applySystemProperties(Environment environment, @Nullable LogFile logFile) {
      new LoggingSystemProperties(environment, this.getDefaultValueResolver(environment), null).apply(logFile);
   }

   protected Function<@Nullable String, @Nullable String> getDefaultValueResolver(Environment environment) {
      String defaultLogCorrelationPattern = this.getDefaultLogCorrelationPattern();
      return name -> {
         String applicationPropertyName = LoggingSystemProperty.CORRELATION_PATTERN.getApplicationPropertyName();
         Assert.state(applicationPropertyName != null, "applicationPropertyName must not be null");
         return StringUtils.hasLength(defaultLogCorrelationPattern)
               && applicationPropertyName.equals(name)
               && environment.getProperty("logging.expect-correlation-id", Boolean.class, false)
            ? defaultLogCorrelationPattern
            : null;
      };
   }

   protected @Nullable String getDefaultLogCorrelationPattern() {
      return null;
   }

   protected static class LogLevels<T> {
      private final Map<LogLevel, T> systemToNative = new EnumMap<>(LogLevel.class);
      private final Map<T, LogLevel> nativeToSystem = new HashMap<>();

      public LogLevels() {
      }

      public void map(LogLevel system, T nativeLevel) {
         this.systemToNative.putIfAbsent(system, nativeLevel);
         this.nativeToSystem.putIfAbsent(nativeLevel, system);
      }

      public @Nullable LogLevel convertNativeToSystem(T level) {
         return this.nativeToSystem.get(level);
      }

      public @Nullable T convertSystemToNative(@Nullable LogLevel level) {
         return this.systemToNative.get(level);
      }

      public Set<LogLevel> getSupported() {
         return new LinkedHashSet<>(this.nativeToSystem.values());
      }
   }
}
