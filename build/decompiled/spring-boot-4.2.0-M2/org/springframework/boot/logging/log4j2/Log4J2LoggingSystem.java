package org.springframework.boot.logging.log4j2;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.AbstractConfiguration;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.ConfigurationException;
import org.apache.logging.log4j.core.config.ConfigurationFactory;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.composite.CompositeConfiguration;
import org.apache.logging.log4j.core.filter.DenyAllFilter;
import org.apache.logging.log4j.core.util.NameUtil;
import org.apache.logging.log4j.jul.Log4jBridgeHandler;
import org.apache.logging.log4j.status.StatusConsoleListener;
import org.apache.logging.log4j.status.StatusLogger;
import org.apache.logging.log4j.util.PropertiesUtil;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.bind.BindResult;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.io.ApplicationResourceLoader;
import org.springframework.boot.logging.AbstractLoggingSystem;
import org.springframework.boot.logging.LogFile;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.logging.LoggingSystemFactory;
import org.springframework.boot.logging.LoggingSystemProperties;
import org.springframework.core.Conventions;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

public class Log4J2LoggingSystem extends AbstractLoggingSystem {
   private static final String OPTIONAL_PREFIX = "optional:";
   static final String LOG4J_BRIDGE_HANDLER = "org.apache.logging.log4j.jul.Log4jBridgeHandler";
   static final String LOG4J_LOG_MANAGER = "org.apache.logging.log4j.jul.LogManager";
   static final String ENVIRONMENT_KEY = Conventions.getQualifiedAttributeName(Log4J2LoggingSystem.class, "environment");
   static final String STATUS_LISTENER_KEY = Conventions.getQualifiedAttributeName(Log4J2LoggingSystem.class, "statusListener");
   private static final AbstractLoggingSystem.LogLevels<Level> LEVELS = new AbstractLoggingSystem.LogLevels<>();
   private static final Filter FILTER = DenyAllFilter.newBuilder().build();
   private static final SpringEnvironmentPropertySource propertySource = new SpringEnvironmentPropertySource();
   private static final Logger statusLogger = StatusLogger.getLogger();
   private final LoggerContext loggerContext;
   private boolean bridgeHandlerInstalled;

   Log4J2LoggingSystem(ClassLoader classLoader) {
      super(classLoader);
      org.apache.logging.log4j.spi.LoggerContext spiLoggerContext = LogManager.getContext(classLoader, false);
      Assert.isInstanceOf(LoggerContext.class, spiLoggerContext);
      this.loggerContext = (LoggerContext)spiLoggerContext;
   }

   @Override
   public LoggingSystemProperties getSystemProperties(ConfigurableEnvironment environment) {
      return new Log4j2LoggingSystemProperties(environment, this.getDefaultValueResolver(environment), null);
   }

   @Override
   protected String[] getStandardConfigLocations() {
      throw new IllegalStateException("Standard config locations cannot be used with Log4J2");
   }

   @Override
   protected @Nullable String getSelfInitializationConfig() {
      return this.getConfigLocation(this.getLoggerContext().getConfiguration());
   }

   @Override
   protected @Nullable String getSpringInitializationConfig() {
      ConfigurationFactory configurationFactory = ConfigurationFactory.getInstance();

      try {
         Configuration springConfiguration = configurationFactory.getConfiguration(this.getLoggerContext(), "-spring", null, this.getClassLoader());
         String configLocation = this.getConfigLocation(springConfiguration);
         return configLocation != null && configLocation.contains("-spring") ? configLocation : null;
      } catch (ConfigurationException ex) {
         statusLogger.warn("Could not load Spring-specific Log4j Core configuration", ex);
         return null;
      }
   }

   private @Nullable String getConfigLocation(Configuration configuration) {
      return configuration.getConfigurationSource().getLocation();
   }

   @Override
   public void beforeInitialize() {
      LoggerContext loggerContext = this.getLoggerContext();
      if (!this.isAlreadyInitialized(loggerContext)) {
         if (!this.configureJdkLoggingBridgeHandler()) {
            super.beforeInitialize();
         }

         loggerContext.getConfiguration().addFilter(FILTER);
      }
   }

   private boolean configureJdkLoggingBridgeHandler() {
      try {
         if (this.isJulUsingASingleConsoleHandlerAtMost() && !this.isLog4jLogManagerInstalled() && this.isLog4jBridgeHandlerAvailable()) {
            this.removeDefaultRootHandler();
            Log4jBridgeHandler.install(false, null, true);
            this.bridgeHandlerInstalled = true;
            return true;
         }
      } catch (Throwable var2) {
      }

      return false;
   }

   private boolean isJulUsingASingleConsoleHandlerAtMost() {
      java.util.logging.Logger rootLogger = java.util.logging.LogManager.getLogManager().getLogger("");
      Handler[] handlers = rootLogger.getHandlers();
      return handlers.length == 0 || handlers.length == 1 && handlers[0] instanceof ConsoleHandler;
   }

   private boolean isLog4jLogManagerInstalled() {
      String logManagerClassName = java.util.logging.LogManager.getLogManager().getClass().getName();
      return "org.apache.logging.log4j.jul.LogManager".equals(logManagerClassName);
   }

   private boolean isLog4jBridgeHandlerAvailable() {
      return ClassUtils.isPresent("org.apache.logging.log4j.jul.Log4jBridgeHandler", this.getClassLoader());
   }

   private void removeLog4jBridgeHandler() {
      this.removeDefaultRootHandler();
      java.util.logging.Logger rootLogger = java.util.logging.LogManager.getLogManager().getLogger("");

      for (Handler handler : rootLogger.getHandlers()) {
         if (handler instanceof Log4jBridgeHandler) {
            handler.close();
            rootLogger.removeHandler(handler);
         }
      }
   }

   private void removeDefaultRootHandler() {
      try {
         java.util.logging.Logger rootLogger = java.util.logging.LogManager.getLogManager().getLogger("");
         Handler[] handlers = rootLogger.getHandlers();
         if (handlers.length == 1 && handlers[0] instanceof ConsoleHandler) {
            rootLogger.removeHandler(handlers[0]);
         }
      } catch (Throwable var3) {
      }
   }

   @Override
   public void initialize(LoggingInitializationContext initializationContext, @Nullable String configLocation, @Nullable LogFile logFile) {
      LoggerContext loggerContext = this.getLoggerContext();
      if (!this.isAlreadyInitialized(loggerContext)) {
         StatusConsoleListener listener = new StatusConsoleListener(Level.WARN);
         StatusLogger.getLogger().registerListener(listener);
         loggerContext.putObject(STATUS_LISTENER_KEY, listener);
         Environment environment = initializationContext.getEnvironment();
         if (environment != null) {
            loggerContext.putObject(ENVIRONMENT_KEY, environment);
            propertySource.setEnvironment(environment);
            PropertiesUtil.getProperties().addPropertySource(propertySource);
         }

         loggerContext.getConfiguration().removeFilter(FILTER);
         super.initialize(initializationContext, configLocation, logFile);
         this.markAsInitialized(loggerContext);
      }
   }

   @Override
   protected void loadDefaults(LoggingInitializationContext initializationContext, @Nullable LogFile logFile) {
      String location = this.getPackagedConfigFile(logFile != null ? "log4j2-file.xml" : "log4j2.xml");
      this.load(initializationContext, location, logFile);
   }

   @Override
   protected void loadConfiguration(LoggingInitializationContext initializationContext, String location, @Nullable LogFile logFile) {
      this.load(initializationContext, location, logFile);
   }

   private void load(LoggingInitializationContext initializationContext, String location, @Nullable LogFile logFile) {
      List<String> overrides = this.getOverrides(initializationContext);
      Environment environment = initializationContext.getEnvironment();
      Assert.state(environment != null, "'environment' must not be null");
      this.applySystemProperties(environment, logFile);
      this.reconfigure(location, overrides);
   }

   private List<String> getOverrides(LoggingInitializationContext initializationContext) {
      Environment environment = initializationContext.getEnvironment();
      Assert.state(environment != null, "'environment' must not be null");
      BindResult<List<String>> overrides = Binder.get(environment).bind("logging.log4j2.config.override", Bindable.listOf(String.class));
      return overrides.orElse(Collections.emptyList());
   }

   private void reconfigure(String location, List<String> overrides) {
      Assert.notNull(location, "'location' must not be null");

      try {
         List<Configuration> configurations = new ArrayList<>();
         ResourceLoader resourceLoader = ApplicationResourceLoader.get(this.getClassLoader());
         configurations.add(this.load(resourceLoader, location));

         for (String override : overrides) {
            Configuration overrideConfiguration = this.loadOverride(resourceLoader, override);
            if (overrideConfiguration != null) {
               configurations.add(overrideConfiguration);
            }
         }

         this.loggerContext.reconfigure(this.mergeConfigurations(configurations));
      } catch (Exception ex) {
         throw new IllegalStateException(
            "Could not initialize Log4J2 logging from %s%s".formatted(location, overrides.isEmpty() ? "" : " with overrides " + overrides), ex
         );
      }
   }

   private Configuration load(ResourceLoader resourceLoader, String location) throws IOException {
      ConfigurationFactory configurationFactory = ConfigurationFactory.getInstance();
      Resource resource = resourceLoader.getResource(location);
      Configuration configuration = configurationFactory.getConfiguration(this.getLoggerContext(), null, resource.getURI(), this.getClassLoader());
      if (configuration == null) {
         throw new ConfigurationException("Could not load Log4j Core configuration from " + location);
      } else {
         return configuration;
      }
   }

   private @Nullable Configuration loadOverride(ResourceLoader resourceLoader, String location) throws IOException {
      if (location.startsWith("optional:")) {
         String actualLocation = location.substring("optional:".length());
         Resource resource = resourceLoader.getResource(actualLocation);

         try {
            return resource.exists() ? this.load(resourceLoader, actualLocation) : null;
         } catch (FileNotFoundException ex) {
            return null;
         }
      } else {
         return this.load(resourceLoader, location);
      }
   }

   private Configuration mergeConfigurations(List<Configuration> configurations) {
      return (Configuration)(configurations.size() == 1
         ? configurations.iterator().next()
         : new CompositeConfiguration(configurations.stream().map(AbstractConfiguration.class::cast).toList()));
   }

   @Override
   protected void reinitialize(LoggingInitializationContext initializationContext) {
      String currentLocation = this.getSelfInitializationConfig();
      Assert.notNull(currentLocation, "'currentLocation' must not be null");
      this.load(initializationContext, currentLocation, null);
   }

   @Override
   public Set<LogLevel> getSupportedLogLevels() {
      return LEVELS.getSupported();
   }

   @Override
   public void setLogLevel(@Nullable String loggerName, @Nullable LogLevel logLevel) {
      this.setLogLevel(loggerName, LEVELS.convertSystemToNative(logLevel));
   }

   private void setLogLevel(@Nullable String loggerName, @Nullable Level level) {
      LoggerConfig logger = this.getLogger(loggerName);
      if (level == null) {
         this.clearLogLevel(loggerName, logger);
      } else {
         this.setLogLevel(loggerName, logger, level);
      }

      this.getLoggerContext().updateLoggers();
   }

   private void clearLogLevel(@Nullable String loggerName, @Nullable LoggerConfig logger) {
      if (logger != null) {
         if (logger instanceof Log4J2LoggingSystem.LevelSetLoggerConfig) {
            this.getLoggerContext().getConfiguration().removeLogger(loggerName);
         } else {
            logger.setLevel(null);
         }
      }
   }

   private void setLogLevel(@Nullable String loggerName, @Nullable LoggerConfig logger, Level level) {
      if (logger == null) {
         this.getLoggerContext().getConfiguration().addLogger(loggerName, new Log4J2LoggingSystem.LevelSetLoggerConfig(loggerName, level, true));
      } else {
         logger.setLevel(level);
      }
   }

   @Override
   public List<LoggerConfiguration> getLoggerConfigurations() {
      List<LoggerConfiguration> result = new ArrayList<>();
      this.getAllLoggers().forEach((name, loggerConfig) -> result.add(this.convertLoggerConfig(name, loggerConfig)));
      result.sort(CONFIGURATION_COMPARATOR);
      return result;
   }

   @Override
   public @Nullable LoggerConfiguration getLoggerConfiguration(String loggerName) {
      LoggerConfig loggerConfig = this.getAllLoggers().get(loggerName);
      return loggerConfig != null ? this.convertLoggerConfig(loggerName, loggerConfig) : null;
   }

   private Map<String, LoggerConfig> getAllLoggers() {
      Map<String, LoggerConfig> loggers = new LinkedHashMap<>();

      for (org.apache.logging.log4j.core.Logger logger : this.getLoggerContext().getLoggers()) {
         this.addLogger(loggers, logger.getName());
      }

      this.getLoggerContext().getConfiguration().getLoggers().keySet().forEach(name -> this.addLogger(loggers, name));
      return loggers;
   }

   private void addLogger(Map<String, LoggerConfig> loggers, String name) {
      Configuration configuration = this.getLoggerContext().getConfiguration();

      while (name != null) {
         loggers.computeIfAbsent(name, configuration::getLoggerConfig);
         name = this.getSubName(name);
      }
   }

   private @Nullable String getSubName(String name) {
      if (!StringUtils.hasLength(name)) {
         return null;
      }

      int nested = name.lastIndexOf(36);
      return nested != -1 ? name.substring(0, nested) : NameUtil.getSubName(name);
   }

   private @Nullable LoggerConfiguration convertLoggerConfig(String name, @Nullable LoggerConfig loggerConfig) {
      if (loggerConfig == null) {
         return null;
      }

      LoggerConfiguration.LevelConfiguration effectiveLevelConfiguration = this.getLevelConfiguration(loggerConfig.getLevel());
      if (!StringUtils.hasLength(name) || "".equals(name)) {
         name = "ROOT";
      }

      boolean isAssigned = loggerConfig.getName().equals(name);
      LoggerConfiguration.LevelConfiguration assignedLevelConfiguration = !isAssigned ? null : effectiveLevelConfiguration;
      return new LoggerConfiguration(name, assignedLevelConfiguration, effectiveLevelConfiguration);
   }

   private LoggerConfiguration.LevelConfiguration getLevelConfiguration(Level level) {
      LogLevel logLevel = LEVELS.convertNativeToSystem(level);
      return logLevel != null ? LoggerConfiguration.LevelConfiguration.of(logLevel) : LoggerConfiguration.LevelConfiguration.ofCustom(level.name());
   }

   @Override
   public Runnable getShutdownHandler() {
      return () -> this.getLoggerContext().stop();
   }

   @Override
   public void cleanUp() {
      if (this.bridgeHandlerInstalled) {
         this.removeLog4jBridgeHandler();
         this.bridgeHandlerInstalled = false;
      }

      super.cleanUp();
      LoggerContext loggerContext = this.getLoggerContext();
      this.markAsUninitialized(loggerContext);
      StatusConsoleListener listener = (StatusConsoleListener)loggerContext.getObject(STATUS_LISTENER_KEY);
      if (listener != null) {
         StatusLogger.getLogger().removeListener(listener);
         loggerContext.removeObject(STATUS_LISTENER_KEY);
      }

      loggerContext.getConfiguration().removeFilter(FILTER);
      propertySource.setEnvironment(null);
      loggerContext.removeObject(ENVIRONMENT_KEY);
   }

   private @Nullable LoggerConfig getLogger(@Nullable String name) {
      return StringUtils.hasLength(name) && !"ROOT".equals(name) ? this.findLogger(name) : this.findLogger("");
   }

   private @Nullable LoggerConfig findLogger(String name) {
      Configuration configuration = this.getLoggerContext().getConfiguration();
      return configuration instanceof AbstractConfiguration abstractConfiguration
         ? abstractConfiguration.getLogger(name)
         : (LoggerConfig)configuration.getLoggers().get(name);
   }

   LoggerContext getLoggerContext() {
      return this.loggerContext;
   }

   private boolean isAlreadyInitialized(LoggerContext loggerContext) {
      return LoggingSystem.class.getName().equals(loggerContext.getExternalContext());
   }

   private void markAsInitialized(LoggerContext loggerContext) {
      loggerContext.setExternalContext(LoggingSystem.class.getName());
   }

   private void markAsUninitialized(LoggerContext loggerContext) {
      loggerContext.setExternalContext(null);
   }

   @Override
   protected String getDefaultLogCorrelationPattern() {
      return "%correlationId";
   }

   public static @Nullable Environment getEnvironment(@Nullable LoggerContext loggerContext) {
      return (Environment)(loggerContext != null ? loggerContext.getObject(ENVIRONMENT_KEY) : null);
   }

   static {
      LEVELS.map(LogLevel.TRACE, Level.TRACE);
      LEVELS.map(LogLevel.DEBUG, Level.DEBUG);
      LEVELS.map(LogLevel.INFO, Level.INFO);
      LEVELS.map(LogLevel.WARN, Level.WARN);
      LEVELS.map(LogLevel.ERROR, Level.ERROR);
      LEVELS.map(LogLevel.FATAL, Level.FATAL);
      LEVELS.map(LogLevel.OFF, Level.OFF);
   }

   @Order(0)
   public static class Factory implements LoggingSystemFactory {
      static final String LOG4J_CORE_CONTEXT_FACTORY = "org.apache.logging.log4j.core.impl.Log4jContextFactory";
      private static final boolean PRESENT = ClassUtils.isPresent(
         "org.apache.logging.log4j.core.impl.Log4jContextFactory", Log4J2LoggingSystem.Factory.class.getClassLoader()
      );

      @Override
      public @Nullable LoggingSystem getLoggingSystem(ClassLoader classLoader) {
         if (PRESENT) {
            try {
               return new Log4J2LoggingSystem(classLoader);
            } catch (IllegalStateException var3) {
            }
         }

         return null;
      }
   }

   private static class LevelSetLoggerConfig extends LoggerConfig {
      LevelSetLoggerConfig(@Nullable String name, Level level, boolean additive) {
         super(name, level, additive);
      }
   }
}
