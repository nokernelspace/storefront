package org.springframework.boot.logging.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.jul.LevelChangePropagator;
import ch.qos.logback.classic.spi.TurboFilterList;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.spi.FilterReply;
import ch.qos.logback.core.status.Status;
import ch.qos.logback.core.status.StatusUtil;
import ch.qos.logback.core.util.StatusPrinter2;
import java.io.PrintStream;
import java.net.URL;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.LogManager;
import org.jspecify.annotations.Nullable;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.slf4j.Marker;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.slf4j.helpers.SubstituteLoggerFactory;
import org.springframework.aot.AotDetector;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.io.ApplicationResourceLoader;
import org.springframework.boot.logging.AbstractLoggingSystem;
import org.springframework.boot.logging.LogFile;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.logging.LoggingSystemFactory;
import org.springframework.boot.logging.LoggingSystemProperties;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

public class LogbackLoggingSystem extends AbstractLoggingSystem implements BeanFactoryInitializationAotProcessor {
   private static final String BRIDGE_HANDLER = "org.slf4j.bridge.SLF4JBridgeHandler";
   private static final String CONFIGURATION_FILE_PROPERTY = "logback.configurationFile";
   private static final AbstractLoggingSystem.LogLevels<Level> LEVELS = createLogLevels();
   private static final TurboFilter SUPPRESS_ALL_FILTER = new TurboFilter() {
      public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params, Throwable t) {
         return FilterReply.DENY;
      }
   };
   private final StatusPrinter2 statusPrinter = new StatusPrinter2();
   private boolean bridgeHandlerInstalled;

   private static AbstractLoggingSystem.LogLevels<Level> createLogLevels() {
      AbstractLoggingSystem.LogLevels<Level> levels = new AbstractLoggingSystem.LogLevels<>();
      levels.map(LogLevel.TRACE, Level.TRACE);
      levels.map(LogLevel.TRACE, Level.ALL);
      levels.map(LogLevel.DEBUG, Level.DEBUG);
      levels.map(LogLevel.INFO, Level.INFO);
      levels.map(LogLevel.WARN, Level.WARN);
      levels.map(LogLevel.ERROR, Level.ERROR);
      levels.map(LogLevel.FATAL, Level.ERROR);
      levels.map(LogLevel.OFF, Level.OFF);
      return levels;
   }

   public LogbackLoggingSystem(ClassLoader classLoader) {
      super(classLoader);
   }

   @Override
   public LoggingSystemProperties getSystemProperties(ConfigurableEnvironment environment) {
      return new LogbackLoggingSystemProperties(environment, this.getDefaultValueResolver(environment), null);
   }

   @Override
   protected String[] getStandardConfigLocations() {
      return new String[]{"logback-test.groovy", "logback-test.xml", "logback.groovy", "logback.xml"};
   }

   @Override
   public void beforeInitialize() {
      LoggerContext loggerContext = this.getLoggerContext();
      if (!this.isAlreadyInitialized(loggerContext)) {
         super.beforeInitialize();
         this.configureJdkLoggingBridgeHandler();
         loggerContext.getTurboFilterList().add(SUPPRESS_ALL_FILTER);
      }
   }

   private void configureJdkLoggingBridgeHandler() {
      try {
         if (this.isBridgeJulIntoSlf4j()) {
            this.removeJdkLoggingBridgeHandler();
            SLF4JBridgeHandler.install();
            this.bridgeHandlerInstalled = true;
         }
      } catch (Throwable var2) {
      }
   }

   private boolean isBridgeJulIntoSlf4j() {
      return this.isBridgeHandlerAvailable() && this.isJulUsingASingleConsoleHandlerAtMost();
   }

   private boolean isBridgeHandlerAvailable() {
      return ClassUtils.isPresent("org.slf4j.bridge.SLF4JBridgeHandler", this.getClassLoader());
   }

   private boolean isJulUsingASingleConsoleHandlerAtMost() {
      java.util.logging.Logger rootLogger = LogManager.getLogManager().getLogger("");
      Handler[] handlers = rootLogger.getHandlers();
      return handlers.length == 0 || handlers.length == 1 && handlers[0] instanceof ConsoleHandler;
   }

   private void removeJdkLoggingBridgeHandler() {
      try {
         this.removeDefaultRootHandler();
         SLF4JBridgeHandler.uninstall();
      } catch (Throwable var2) {
      }
   }

   private void removeDefaultRootHandler() {
      try {
         java.util.logging.Logger rootLogger = LogManager.getLogManager().getLogger("");
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
      this.putInitializationContextObjects(loggerContext, initializationContext);
      if (!this.isAlreadyInitialized(loggerContext)) {
         if (!this.initializeFromAotGeneratedArtifactsIfPossible(initializationContext, logFile)) {
            super.initialize(initializationContext, configLocation, logFile);
         }

         loggerContext.getTurboFilterList().remove(SUPPRESS_ALL_FILTER);
         this.markAsInitialized(loggerContext);
         if (StringUtils.hasText(System.getProperty("logback.configurationFile"))) {
            this.getLogger(LogbackLoggingSystem.class.getName())
               .warn("Ignoring 'logback.configurationFile' system property. Please use 'logging.config' instead.");
         }
      }
   }

   private boolean initializeFromAotGeneratedArtifactsIfPossible(LoggingInitializationContext initializationContext, @Nullable LogFile logFile) {
      if (!AotDetector.useGeneratedArtifacts()) {
         return false;
      }

      if (initializationContext != null) {
         Environment environment = initializationContext.getEnvironment();
         Assert.state(environment != null, "'environment' must not be null");
         this.applySystemProperties(environment, logFile);
      }

      LoggerContext loggerContext = this.getLoggerContext();
      this.stopAndReset(loggerContext);
      this.withLoggingSuppressed(() -> this.putInitializationContextObjects(loggerContext, initializationContext));
      SystemStatusListener.addTo(loggerContext);
      SpringBootJoranConfigurator configurator = new SpringBootJoranConfigurator(initializationContext);
      configurator.setContext(loggerContext);
      boolean configuredUsingAotGeneratedArtifacts = configurator.configureUsingAotGeneratedArtifacts();
      if (configuredUsingAotGeneratedArtifacts) {
         this.reportConfigurationErrorsIfNecessary(loggerContext);
      }

      return configuredUsingAotGeneratedArtifacts;
   }

   @Override
   protected void loadDefaults(LoggingInitializationContext initializationContext, @Nullable LogFile logFile) {
      LoggerContext loggerContext = this.getLoggerContext();
      this.stopAndReset(loggerContext);
      this.withLoggingSuppressed(() -> {
         boolean debug = Boolean.getBoolean("logback.debug");
         this.putInitializationContextObjects(loggerContext, initializationContext);
         SystemStatusListener.addTo(loggerContext, debug);
         Environment environment = initializationContext.getEnvironment();
         Assert.state(environment != null, "'environment' must not be null");
         new LogbackLoggingSystemProperties(environment, this.getDefaultValueResolver(environment), loggerContext::putProperty).apply(logFile);
         LogbackConfigurator configurator = !debug ? new LogbackConfigurator(loggerContext) : new DebugLogbackConfigurator(loggerContext);
         new DefaultLogbackConfiguration(logFile).apply(configurator);
         loggerContext.setPackagingDataEnabled(true);
         loggerContext.start();
      });
   }

   @Override
   protected void loadConfiguration(LoggingInitializationContext initializationContext, String location, @Nullable LogFile logFile) {
      LoggerContext loggerContext = this.getLoggerContext();
      this.stopAndReset(loggerContext);
      this.withLoggingSuppressed(() -> {
         this.putInitializationContextObjects(loggerContext, initializationContext);
         if (initializationContext != null) {
            Environment environment = initializationContext.getEnvironment();
            Assert.state(environment != null, "'environment' must not be null");
            this.applySystemProperties(environment, logFile);
         }

         SystemStatusListener.addTo(loggerContext);

         try {
            Resource resource = ApplicationResourceLoader.get().getResource(location);
            this.configureByResourceUrl(initializationContext, loggerContext, resource.getURL());
         } catch (Exception ex) {
            throw new IllegalStateException("Could not initialize Logback logging from " + location, ex);
         }

         loggerContext.start();
      });
      this.reportConfigurationErrorsIfNecessary(loggerContext);
   }

   private void reportConfigurationErrorsIfNecessary(LoggerContext loggerContext) {
      StringBuilder errors = new StringBuilder();
      List<Throwable> suppressedExceptions = new ArrayList<>();

      for (Status status : loggerContext.getStatusManager().getCopyOfStatusList()) {
         if (status.getLevel() == 2) {
            errors.append(!errors.isEmpty() ? String.format("%n") : "");
            errors.append(status);
            if (status.getThrowable() != null) {
               suppressedExceptions.add(status.getThrowable());
            }
         }
      }

      if (errors.isEmpty()) {
         if (!StatusUtil.contextHasStatusListener(loggerContext)) {
            this.statusPrinter.printInCaseOfErrorsOrWarnings(loggerContext);
         }
      } else {
         IllegalStateException ex = new IllegalStateException(String.format("Logback configuration error detected: %n%s", errors));
         suppressedExceptions.forEach(ex::addSuppressed);
         throw ex;
      }
   }

   private void configureByResourceUrl(LoggingInitializationContext initializationContext, LoggerContext loggerContext, URL url) throws JoranException {
      JoranConfigurator configurator = new SpringBootJoranConfigurator(initializationContext);
      configurator.setContext(loggerContext);
      configurator.doConfigure(url);
   }

   private void stopAndReset(LoggerContext loggerContext) {
      loggerContext.stop();
      loggerContext.reset();
      if (this.isBridgeHandlerInstalled()) {
         this.addLevelChangePropagator(loggerContext);
      }
   }

   private boolean isBridgeHandlerInstalled() {
      if (!this.isBridgeHandlerAvailable()) {
         return false;
      }

      java.util.logging.Logger rootLogger = LogManager.getLogManager().getLogger("");
      Handler[] handlers = rootLogger.getHandlers();
      return handlers.length == 1 && handlers[0] instanceof SLF4JBridgeHandler;
   }

   private void addLevelChangePropagator(LoggerContext loggerContext) {
      LevelChangePropagator levelChangePropagator = new LevelChangePropagator();
      levelChangePropagator.setResetJUL(true);
      levelChangePropagator.setContext(loggerContext);
      loggerContext.addListener(levelChangePropagator);
   }

   @Override
   public void cleanUp() {
      LoggerContext context = this.getLoggerContext();
      this.markAsUninitialized(context);
      super.cleanUp();
      if (this.bridgeHandlerInstalled) {
         this.removeJdkLoggingBridgeHandler();
         this.bridgeHandlerInstalled = false;
      }

      context.getStatusManager().clear();
      context.getTurboFilterList().remove(SUPPRESS_ALL_FILTER);
   }

   @Override
   protected void reinitialize(LoggingInitializationContext initializationContext) {
      LoggerContext loggerContext = this.getLoggerContext();
      loggerContext.reset();
      loggerContext.getStatusManager().clear();
      String location = this.getSelfInitializationConfig();
      Assert.state(location != null, "location must not be null");
      this.loadConfiguration(initializationContext, location, null);
   }

   private void putInitializationContextObjects(LoggerContext loggerContext, LoggingInitializationContext initializationContext) {
      this.withLoggingSuppressed(() -> loggerContext.putObject(Environment.class.getName(), initializationContext.getEnvironment()));
   }

   @Override
   public List<LoggerConfiguration> getLoggerConfigurations() {
      List<LoggerConfiguration> result = new ArrayList<>();

      for (Logger logger : this.getLoggerContext().getLoggerList()) {
         result.add(this.getLoggerConfiguration(logger));
      }

      result.sort(CONFIGURATION_COMPARATOR);
      return result;
   }

   @Override
   public @Nullable LoggerConfiguration getLoggerConfiguration(String loggerName) {
      String name = this.getLoggerName(loggerName);
      LoggerContext loggerContext = this.getLoggerContext();
      return this.getLoggerConfiguration(loggerContext.exists(name));
   }

   private String getLoggerName(@Nullable String name) {
      return StringUtils.hasLength(name) && !"ROOT".equals(name) ? name : "ROOT";
   }

   private @Nullable LoggerConfiguration getLoggerConfiguration(@Nullable Logger logger) {
      if (logger == null) {
         return null;
      }

      LogLevel level = LEVELS.convertNativeToSystem(logger.getLevel());
      LogLevel effectiveLevel = LEVELS.convertNativeToSystem(logger.getEffectiveLevel());
      String name = this.getLoggerName(logger.getName());
      Assert.state(effectiveLevel != null, "effectiveLevel must not be null");
      return new LoggerConfiguration(name, level, effectiveLevel);
   }

   @Override
   public Set<LogLevel> getSupportedLogLevels() {
      return LEVELS.getSupported();
   }

   @Override
   public void setLogLevel(@Nullable String loggerName, @Nullable LogLevel level) {
      Logger logger = this.getLogger(loggerName);
      if (logger != null) {
         logger.setLevel(LEVELS.convertSystemToNative(level));
      }
   }

   @Override
   public Runnable getShutdownHandler() {
      return () -> this.getLoggerContext().stop();
   }

   private Logger getLogger(@Nullable String name) {
      LoggerContext factory = this.getLoggerContext();
      return factory.getLogger(this.getLoggerName(name));
   }

   private LoggerContext getLoggerContext() {
      ILoggerFactory factory = this.getLoggerFactory();
      Assert.state(
         factory instanceof LoggerContext,
         () -> String.format(
            "LoggerFactory is not a Logback LoggerContext but Logback is on the classpath. Either remove Logback or the competing implementation (%s loaded from %s). If you are using WebLogic you will need to add 'org.slf4j' to prefer-application-packages in WEB-INF/weblogic.xml",
            factory.getClass(),
            this.getLocation(factory)
         )
      );
      return (LoggerContext)factory;
   }

   private ILoggerFactory getLoggerFactory() {
      ILoggerFactory factory;
      for (factory = LoggerFactory.getILoggerFactory(); factory instanceof SubstituteLoggerFactory; factory = LoggerFactory.getILoggerFactory()) {
         try {
            Thread.sleep(50L);
         } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for non-substitute logger factory", ex);
         }
      }

      return factory;
   }

   private Object getLocation(ILoggerFactory factory) {
      try {
         ProtectionDomain protectionDomain = factory.getClass().getProtectionDomain();
         CodeSource codeSource = protectionDomain.getCodeSource();
         if (codeSource != null) {
            return codeSource.getLocation();
         }
      } catch (SecurityException var4) {
      }

      return "unknown location";
   }

   private boolean isAlreadyInitialized(LoggerContext loggerContext) {
      return loggerContext.getObject(LoggingSystem.class.getName()) != null;
   }

   private void markAsInitialized(LoggerContext loggerContext) {
      loggerContext.putObject(LoggingSystem.class.getName(), new Object());
   }

   private void markAsUninitialized(LoggerContext loggerContext) {
      loggerContext.removeObject(LoggingSystem.class.getName());
   }

   @Override
   protected String getDefaultLogCorrelationPattern() {
      return "%correlationId";
   }

   public BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
      String key = BeanFactoryInitializationAotContribution.class.getName();
      LoggerContext context = this.getLoggerContext();
      BeanFactoryInitializationAotContribution contribution = (BeanFactoryInitializationAotContribution)context.getObject(key);
      context.removeObject(key);
      return contribution;
   }

   private void withLoggingSuppressed(Runnable action) {
      TurboFilterList turboFilters = this.getLoggerContext().getTurboFilterList();
      turboFilters.add(SUPPRESS_ALL_FILTER);

      try {
         action.run();
      } finally {
         turboFilters.remove(SUPPRESS_ALL_FILTER);
      }
   }

   void setStatusPrinterStream(PrintStream stream) {
      this.statusPrinter.setPrintStream(stream);
   }

   @Order(-2147482624)
   public static class Factory implements LoggingSystemFactory {
      private static final boolean PRESENT = ClassUtils.isPresent("ch.qos.logback.classic.LoggerContext", LogbackLoggingSystem.Factory.class.getClassLoader());

      @Override
      public @Nullable LoggingSystem getLoggingSystem(ClassLoader classLoader) {
         return PRESENT ? new LogbackLoggingSystem(classLoader) : null;
      }
   }
}
