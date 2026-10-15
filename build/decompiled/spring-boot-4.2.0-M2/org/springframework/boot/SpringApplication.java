package org.springframework.boot;

import java.lang.StackWalker.Option;
import java.lang.StackWalker.StackFrame;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.crac.management.CRaCMXBean;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.AotDetector;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractAutowireCapableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.bootstrap.BootstrapRegistryInitializer;
import org.springframework.boot.bootstrap.DefaultBootstrapContext;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.boot.system.JavaVersion;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.aot.AotApplicationContextInitializer;
import org.springframework.context.event.ApplicationContextEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.NativeDetector;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.OrderComparator.OrderSourceProvider;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.CompositePropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.core.io.support.SpringFactoriesLoader.ArgumentResolver;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.function.ThrowingConsumer;
import org.springframework.util.function.ThrowingSupplier;

public class SpringApplication {
   public static final String BANNER_LOCATION_PROPERTY_VALUE = "banner.txt";
   public static final String BANNER_LOCATION_PROPERTY = "spring.banner.location";
   private static final String SYSTEM_PROPERTY_JAVA_AWT_HEADLESS = "java.awt.headless";
   private static final Log logger = LogFactory.getLog(SpringApplication.class);
   static final SpringApplicationShutdownHook shutdownHook = new SpringApplicationShutdownHook();
   private static final ThreadLocal<SpringApplicationHook> applicationHook = new ThreadLocal<>();
   private final Set<Class<?>> primarySources;
   private @Nullable Class<?> mainApplicationClass;
   private boolean addCommandLineProperties = true;
   private boolean addConversionService = true;
   private @Nullable Banner banner;
   private @Nullable ResourceLoader resourceLoader;
   private @Nullable BeanNameGenerator beanNameGenerator;
   private @Nullable ConfigurableEnvironment environment;
   private boolean headless = true;
   private List<ApplicationContextInitializer<?>> initializers = new ArrayList<>();
   private List<ApplicationListener<?>> listeners = new ArrayList<>();
   private @Nullable Map<String, Object> defaultProperties;
   private final List<BootstrapRegistryInitializer> bootstrapRegistryInitializers;
   private Set<String> additionalProfiles = Collections.emptySet();
   private boolean isCustomEnvironment;
   private @Nullable String environmentPrefix;
   private ApplicationContextFactory applicationContextFactory = ApplicationContextFactory.DEFAULT;
   private ApplicationStartup applicationStartup = ApplicationStartup.DEFAULT;
   final ApplicationProperties properties = new ApplicationProperties();

   public SpringApplication(Class<?>... primarySources) {
      this(null, primarySources);
   }

   public SpringApplication(@Nullable ResourceLoader resourceLoader, Class<?>... primarySources) {
      this.resourceLoader = resourceLoader;
      Assert.notNull(primarySources, "'primarySources' must not be null");
      this.primarySources = new LinkedHashSet<>(Arrays.asList(primarySources));
      this.properties.setWebApplicationType(WebApplicationType.deduce());
      this.bootstrapRegistryInitializers = new ArrayList<>(this.getSpringFactoriesInstances(BootstrapRegistryInitializer.class));
      this.setInitializers(this.getSpringFactoriesInstances(ApplicationContextInitializer.class));
      this.setListeners(this.getSpringFactoriesInstances(ApplicationListener.class));
      this.mainApplicationClass = this.deduceMainApplicationClass();
   }

   private @Nullable Class<?> deduceMainApplicationClass() {
      return StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE).walk(this::findMainClass).orElse(null);
   }

   private Optional<Class<?>> findMainClass(Stream<StackFrame> stack) {
      return stack.filter(frame -> Objects.equals(frame.getMethodName(), "main")).findFirst().map(StackFrame::getDeclaringClass);
   }

   public ConfigurableApplicationContext run(String... args) {
      SpringApplication.Startup startup = SpringApplication.Startup.create();
      if (this.properties.isRegisterShutdownHook()) {
         shutdownHook.enableShutdownHookAddition();
      }

      DefaultBootstrapContext bootstrapContext = this.createBootstrapContext();
      ConfigurableApplicationContext context = null;
      this.configureHeadlessProperty();
      SpringApplicationRunListeners listeners = this.getRunListeners(args);
      listeners.starting(bootstrapContext, this.mainApplicationClass);

      try {
         ApplicationArguments applicationArguments = new DefaultApplicationArguments(args);
         ConfigurableEnvironment environment = this.prepareEnvironment(listeners, bootstrapContext, applicationArguments);
         Banner printedBanner = this.printBanner(environment);
         context = this.createApplicationContext();
         context.setApplicationStartup(this.applicationStartup);
         this.prepareContext(bootstrapContext, context, environment, listeners, applicationArguments, printedBanner);
         this.refreshContext(context);
         this.afterRefresh(context, applicationArguments);
         Duration timeTakenToStarted = startup.started();
         if (this.properties.isLogStartupInfo()) {
            new StartupInfoLogger(this.mainApplicationClass, environment).logStarted(this.getApplicationLog(), startup);
         }

         listeners.started(context, timeTakenToStarted);
         this.callRunners(context, applicationArguments);
      } catch (Throwable ex) {
         throw this.handleRunFailure(context, ex, listeners);
      }

      try {
         if (context.isRunning()) {
            listeners.ready(context, startup.ready());
         }

         return context;
      } catch (Throwable ex) {
         throw this.handleRunFailure(context, ex, null);
      }
   }

   private DefaultBootstrapContext createBootstrapContext() {
      DefaultBootstrapContext bootstrapContext = new DefaultBootstrapContext();
      this.bootstrapRegistryInitializers.forEach(initializer -> initializer.initialize(bootstrapContext));
      return bootstrapContext;
   }

   private ConfigurableEnvironment prepareEnvironment(
      SpringApplicationRunListeners listeners, DefaultBootstrapContext bootstrapContext, ApplicationArguments applicationArguments
   ) {
      ConfigurableEnvironment environment = this.getOrCreateEnvironment();
      this.configureEnvironment(environment, applicationArguments.getSourceArgs());
      ConfigurationPropertySources.attach(environment);
      listeners.environmentPrepared(bootstrapContext, environment);
      ApplicationInfoPropertySource.moveToEnd(environment);
      DefaultPropertiesPropertySource.moveToEnd(environment);
      Assert.state(!environment.containsProperty("spring.main.environment-prefix"), "Environment prefix cannot be set via properties.");
      this.bindToSpringApplication(environment);
      if (!this.isCustomEnvironment) {
         EnvironmentConverter environmentConverter = new EnvironmentConverter(this.getClassLoader());
         environment = environmentConverter.convertEnvironmentIfNecessary(environment, this.deduceEnvironmentClass());
      }

      ConfigurationPropertySources.attach(environment);
      return environment;
   }

   private Class<? extends ConfigurableEnvironment> deduceEnvironmentClass() {
      WebApplicationType webApplicationType = this.properties.getWebApplicationType();
      Class<? extends ConfigurableEnvironment> environmentType = this.applicationContextFactory.getEnvironmentType(webApplicationType);
      if (environmentType == null && this.applicationContextFactory != ApplicationContextFactory.DEFAULT) {
         environmentType = ApplicationContextFactory.DEFAULT.getEnvironmentType(webApplicationType);
      }

      return environmentType != null ? environmentType : ApplicationEnvironment.class;
   }

   private void prepareContext(
      DefaultBootstrapContext bootstrapContext,
      ConfigurableApplicationContext context,
      ConfigurableEnvironment environment,
      SpringApplicationRunListeners listeners,
      ApplicationArguments applicationArguments,
      @Nullable Banner printedBanner
   ) {
      context.setEnvironment(environment);
      this.postProcessApplicationContext(context);
      this.addAotGeneratedInitializerIfNecessary(this.initializers);
      ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
      if (beanFactory instanceof AbstractAutowireCapableBeanFactory autowireCapableBeanFactory) {
         autowireCapableBeanFactory.setAllowCircularReferences(this.properties.isAllowCircularReferences());
         if (beanFactory instanceof DefaultListableBeanFactory listableBeanFactory) {
            listableBeanFactory.setAllowBeanDefinitionOverriding(this.properties.isAllowBeanDefinitionOverriding());
         }
      }

      this.applyInitializers(context);
      listeners.contextPrepared(context);
      bootstrapContext.close(context);
      if (this.properties.isLogStartupInfo()) {
         this.logStartupInfo(context);
         this.logStartupProfileInfo(context);
      }

      beanFactory.registerSingleton("springApplicationArguments", applicationArguments);
      if (printedBanner != null) {
         beanFactory.registerSingleton("springBootBanner", printedBanner);
      }

      if (this.properties.isLazyInitialization()) {
         context.addBeanFactoryPostProcessor(new LazyInitializationBeanFactoryPostProcessor());
      }

      if (this.properties.isKeepAlive()) {
         context.addApplicationListener(new SpringApplication.KeepAlive());
      }

      context.addBeanFactoryPostProcessor(new SpringApplication.PropertySourceOrderingBeanFactoryPostProcessor(context));
      if (!AotDetector.useGeneratedArtifacts()) {
         Set<Object> sources = this.getAllSources();
         Assert.state(!ObjectUtils.isEmpty(sources), "No sources defined");
         this.load(context, sources.toArray(new Object[0]));
      }

      listeners.contextLoaded(context);
   }

   private void addAotGeneratedInitializerIfNecessary(List<ApplicationContextInitializer<?>> initializers) {
      if (AotDetector.useGeneratedArtifacts()) {
         List<ApplicationContextInitializer<?>> aotInitializers = new ArrayList<>(
            initializers.stream().filter(AotApplicationContextInitializer.class::isInstance).toList()
         );
         if (aotInitializers.isEmpty()) {
            Assert.state(this.mainApplicationClass != null, "No application main class found");
            String initializerClassName = this.mainApplicationClass.getName() + "__ApplicationContextInitializer";
            if (!ClassUtils.isPresent(initializerClassName, this.getClassLoader())) {
               throw new AotInitializerNotFoundException(this.mainApplicationClass, initializerClassName);
            }

            aotInitializers.add(AotApplicationContextInitializer.forInitializerClasses(new String[]{initializerClassName}));
         }

         initializers.removeAll(aotInitializers);
         initializers.addAll(0, aotInitializers);
      }

      if (NativeDetector.inNativeImage()) {
         SpringApplication.NativeImageRequirementsException.throwIfNotMet();
      }
   }

   private void refreshContext(ConfigurableApplicationContext context) {
      if (this.properties.isRegisterShutdownHook()) {
         shutdownHook.registerApplicationContext(context);
      }

      this.refresh(context);
   }

   private void configureHeadlessProperty() {
      System.setProperty("java.awt.headless", System.getProperty("java.awt.headless", Boolean.toString(this.headless)));
   }

   private SpringApplicationRunListeners getRunListeners(String[] args) {
      ArgumentResolver argumentResolver = ArgumentResolver.of(SpringApplication.class, this);
      argumentResolver = argumentResolver.and(String[].class, args);
      List<SpringApplicationRunListener> listeners = this.getSpringFactoriesInstances(SpringApplicationRunListener.class, argumentResolver);
      SpringApplicationHook hook = applicationHook.get();
      SpringApplicationRunListener hookListener = hook != null ? hook.getRunListener(this) : null;
      if (hookListener != null) {
         listeners = new ArrayList<>(listeners);
         listeners.add(hookListener);
      }

      return new SpringApplicationRunListeners(logger, listeners, this.applicationStartup);
   }

   private <T> List<T> getSpringFactoriesInstances(Class<T> type) {
      return this.getSpringFactoriesInstances(type, null);
   }

   private <T> List<T> getSpringFactoriesInstances(Class<T> type, @Nullable ArgumentResolver argumentResolver) {
      return SpringFactoriesLoader.forDefaultResourceLocation(this.getClassLoader()).load(type, argumentResolver);
   }

   private ConfigurableEnvironment getOrCreateEnvironment() {
      if (this.environment != null) {
         return this.environment;
      }

      WebApplicationType webApplicationType = this.properties.getWebApplicationType();
      ConfigurableEnvironment environment = this.applicationContextFactory.createEnvironment(webApplicationType);
      if (environment == null && this.applicationContextFactory != ApplicationContextFactory.DEFAULT) {
         environment = ApplicationContextFactory.DEFAULT.createEnvironment(webApplicationType);
      }

      return (ConfigurableEnvironment)(environment != null ? environment : new ApplicationEnvironment());
   }

   protected void configureEnvironment(ConfigurableEnvironment environment, String[] args) {
      if (this.addConversionService) {
         environment.setConversionService(new ApplicationConversionService());
      }

      this.configurePropertySources(environment, args);
      this.configureProfiles(environment, args);
   }

   protected void configurePropertySources(ConfigurableEnvironment environment, String[] args) {
      MutablePropertySources sources = environment.getPropertySources();
      if (!CollectionUtils.isEmpty(this.defaultProperties)) {
         DefaultPropertiesPropertySource.addOrMerge(this.defaultProperties, sources);
      }

      if (this.addCommandLineProperties && args.length > 0) {
         String name = "commandLineArgs";
         PropertySource<?> source = sources.get(name);
         if (source != null) {
            CompositePropertySource composite = new CompositePropertySource(name);
            composite.addPropertySource(new SimpleCommandLinePropertySource("springApplicationCommandLineArgs", args));
            composite.addPropertySource(source);
            sources.replace(name, composite);
         } else {
            sources.addFirst(new SimpleCommandLinePropertySource(args));
         }
      }

      environment.getPropertySources().addLast(new ApplicationInfoPropertySource(this.mainApplicationClass));
   }

   protected void configureProfiles(ConfigurableEnvironment environment, String[] args) {
   }

   protected void bindToSpringApplication(ConfigurableEnvironment environment) {
      try {
         Binder.get(environment).bind("spring.main", Bindable.ofInstance(this.properties));
      } catch (Exception ex) {
         throw new IllegalStateException("Cannot bind to SpringApplication", ex);
      }
   }

   private @Nullable Banner printBanner(ConfigurableEnvironment environment) {
      if (this.properties.getBannerMode(environment) == Banner.Mode.OFF) {
         return null;
      }

      ResourceLoader resourceLoader = (ResourceLoader)(this.resourceLoader != null ? this.resourceLoader : new DefaultResourceLoader(null));
      SpringApplicationBannerPrinter bannerPrinter = new SpringApplicationBannerPrinter(resourceLoader, this.banner);
      return this.properties.getBannerMode(environment) == Banner.Mode.LOG
         ? bannerPrinter.print(environment, this.mainApplicationClass, logger)
         : bannerPrinter.print(environment, this.mainApplicationClass, System.out);
   }

   protected ConfigurableApplicationContext createApplicationContext() {
      ConfigurableApplicationContext context = this.applicationContextFactory.create(this.properties.getWebApplicationType());
      Assert.state(context != null, "ApplicationContextFactory created null context");
      return context;
   }

   protected void postProcessApplicationContext(ConfigurableApplicationContext context) {
      if (this.beanNameGenerator != null) {
         context.getBeanFactory().registerSingleton("org.springframework.context.annotation.internalConfigurationBeanNameGenerator", this.beanNameGenerator);
      }

      if (this.resourceLoader != null) {
         if (context instanceof GenericApplicationContext genericApplicationContext) {
            genericApplicationContext.setResourceLoader(this.resourceLoader);
         }

         if (context instanceof DefaultResourceLoader defaultResourceLoader) {
            defaultResourceLoader.setClassLoader(this.resourceLoader.getClassLoader());
         }
      }

      if (this.addConversionService) {
         context.getBeanFactory().setConversionService(context.getEnvironment().getConversionService());
      }
   }

   protected void applyInitializers(ConfigurableApplicationContext context) {
      for (ApplicationContextInitializer initializer : this.getInitializers()) {
         Class<?> requiredType = GenericTypeResolver.resolveTypeArgument(initializer.getClass(), ApplicationContextInitializer.class);
         Assert.state(requiredType != null, () -> "No generic type found for initializr of type " + initializer.getClass());
         Assert.state(requiredType.isInstance(context), "Unable to call initializer");
         initializer.initialize(context);
      }
   }

   protected void logStartupInfo(ConfigurableApplicationContext context) {
      boolean isRoot = context.getParent() == null;
      if (isRoot) {
         new StartupInfoLogger(this.mainApplicationClass, context.getEnvironment()).logStarting(this.getApplicationLog());
      }
   }

   protected void logStartupProfileInfo(ConfigurableApplicationContext context) {
      Log log = this.getApplicationLog();
      if (log.isInfoEnabled()) {
         List<String> activeProfiles = this.quoteProfiles(context.getEnvironment().getActiveProfiles());
         if (ObjectUtils.isEmpty(activeProfiles)) {
            List<String> defaultProfiles = this.quoteProfiles(context.getEnvironment().getDefaultProfiles());
            String message = String.format("%s default %s: ", defaultProfiles.size(), defaultProfiles.size() <= 1 ? "profile" : "profiles");
            log.info("No active profile set, falling back to " + message + StringUtils.collectionToDelimitedString(defaultProfiles, ", "));
         } else {
            String message = activeProfiles.size() == 1 ? "1 profile is active: " : activeProfiles.size() + " profiles are active: ";
            log.info("The following " + message + StringUtils.collectionToDelimitedString(activeProfiles, ", "));
         }
      }
   }

   private List<String> quoteProfiles(String[] profiles) {
      return Arrays.stream(profiles).map(profile -> "\"" + profile + "\"").toList();
   }

   protected Log getApplicationLog() {
      return this.mainApplicationClass == null ? logger : LogFactory.getLog(this.mainApplicationClass);
   }

   protected void load(ApplicationContext context, Object[] sources) {
      if (logger.isDebugEnabled()) {
         logger.debug("Loading source " + StringUtils.arrayToCommaDelimitedString(sources));
      }

      BeanDefinitionLoader loader = this.createBeanDefinitionLoader(this.getBeanDefinitionRegistry(context), sources);
      if (this.beanNameGenerator != null) {
         loader.setBeanNameGenerator(this.beanNameGenerator);
      }

      if (this.resourceLoader != null) {
         loader.setResourceLoader(this.resourceLoader);
      }

      if (this.environment != null) {
         loader.setEnvironment(this.environment);
      }

      loader.load();
   }

   public @Nullable ResourceLoader getResourceLoader() {
      return this.resourceLoader;
   }

   public ClassLoader getClassLoader() {
      if (this.resourceLoader != null) {
         ClassLoader classLoader = this.resourceLoader.getClassLoader();
         Assert.state(classLoader != null, "No classloader found");
         return classLoader;
      } else {
         ClassLoader classLoader = ClassUtils.getDefaultClassLoader();
         Assert.state(classLoader != null, "No classloader found");
         return classLoader;
      }
   }

   private BeanDefinitionRegistry getBeanDefinitionRegistry(ApplicationContext context) {
      if (context instanceof BeanDefinitionRegistry registry) {
         return registry;
      } else if (context instanceof AbstractApplicationContext abstractApplicationContext) {
         return (BeanDefinitionRegistry)abstractApplicationContext.getBeanFactory();
      } else {
         throw new IllegalStateException("Could not locate BeanDefinitionRegistry");
      }
   }

   protected BeanDefinitionLoader createBeanDefinitionLoader(BeanDefinitionRegistry registry, Object[] sources) {
      return new BeanDefinitionLoader(registry, sources);
   }

   protected void refresh(ConfigurableApplicationContext applicationContext) {
      applicationContext.refresh();
   }

   protected void afterRefresh(ConfigurableApplicationContext context, ApplicationArguments args) {
   }

   private void callRunners(ConfigurableApplicationContext context, ApplicationArguments args) {
      ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
      String[] beanNames = beanFactory.getBeanNamesForType(Runner.class);
      Map<Runner, String> instancesToBeanNames = new IdentityHashMap<>();

      for (String beanName : beanNames) {
         instancesToBeanNames.put((Runner)beanFactory.getBean(beanName, Runner.class), beanName);
      }

      Comparator<Object> comparator = this.getOrderComparator(beanFactory)
         .withSourceProvider(new SpringApplication.FactoryAwareOrderSourceProvider(beanFactory, instancesToBeanNames));
      instancesToBeanNames.keySet().stream().sorted(comparator).forEach(runner -> this.callRunner((Runner)runner, args));
   }

   private OrderComparator getOrderComparator(ConfigurableListableBeanFactory beanFactory) {
      return (OrderComparator)((
            beanFactory instanceof DefaultListableBeanFactory defaultListableBeanFactory ? defaultListableBeanFactory.getDependencyComparator() : null
         ) instanceof OrderComparator orderComparator
         ? orderComparator
         : AnnotationAwareOrderComparator.INSTANCE);
   }

   private void callRunner(Runner runner, ApplicationArguments args) {
      if (runner instanceof ApplicationRunner) {
         this.callRunner(ApplicationRunner.class, runner, applicationRunner -> applicationRunner.run(args));
      }

      if (runner instanceof CommandLineRunner) {
         this.callRunner(CommandLineRunner.class, runner, commandLineRunner -> commandLineRunner.run(args.getSourceArgs()));
      }
   }

   private <R extends Runner> void callRunner(Class<R> type, Runner runner, ThrowingConsumer<R> call) {
      call.throwing((message, ex) -> new IllegalStateException("Failed to execute " + ClassUtils.getShortName(type), ex)).accept(runner);
   }

   private RuntimeException handleRunFailure(
      @Nullable ConfigurableApplicationContext context, Throwable exception, @Nullable SpringApplicationRunListeners listeners
   ) {
      if (exception instanceof SpringApplication.AbandonedRunException abandonedRunException) {
         return abandonedRunException;
      } else {
         try {
            try {
               this.handleExitCode(context, exception);
               if (listeners != null) {
                  listeners.failed(context, exception);
               }
            } finally {
               this.reportFailure(this.getExceptionReporters(context), exception);
               if (context != null) {
                  context.close();
                  shutdownHook.deregisterFailedApplicationContext(context);
               }
            }
         } catch (Exception ex) {
            logger.warn("Unable to close ApplicationContext", ex);
         }

         return exception instanceof RuntimeException runtimeException ? runtimeException : new IllegalStateException(exception);
      }
   }

   private Collection<SpringBootExceptionReporter> getExceptionReporters(@Nullable ConfigurableApplicationContext context) {
      try {
         ArgumentResolver argumentResolver = context != null ? ArgumentResolver.of(ConfigurableApplicationContext.class, context) : ArgumentResolver.none();
         return this.getSpringFactoriesInstances(SpringBootExceptionReporter.class, argumentResolver);
      } catch (Throwable ex) {
         return Collections.emptyList();
      }
   }

   private void reportFailure(Collection<SpringBootExceptionReporter> exceptionReporters, Throwable failure) {
      try {
         for (SpringBootExceptionReporter reporter : exceptionReporters) {
            if (reporter.reportException(failure)) {
               this.registerLoggedException(failure);
               return;
            }
         }
      } catch (Throwable var5) {
      }

      if (logger.isErrorEnabled()) {
         if (NativeDetector.inNativeImage()) {
            System.out.println("Application run failed");
            failure.printStackTrace(System.out);
         } else {
            logger.error("Application run failed", failure);
         }

         this.registerLoggedException(failure);
      }
   }

   protected void registerLoggedException(Throwable exception) {
      SpringBootExceptionHandler handler = this.getSpringBootExceptionHandler();
      if (handler != null) {
         handler.registerLoggedException(exception);
      }
   }

   private void handleExitCode(@Nullable ConfigurableApplicationContext context, Throwable exception) {
      int exitCode = this.getExitCodeFromException(context, exception);
      if (exitCode != 0) {
         if (context != null) {
            context.publishEvent(new ExitCodeEvent(context, exitCode));
         }

         SpringBootExceptionHandler handler = this.getSpringBootExceptionHandler();
         if (handler != null) {
            handler.registerExitCode(exitCode);
         }
      }
   }

   private int getExitCodeFromException(@Nullable ConfigurableApplicationContext context, Throwable exception) {
      int exitCode = this.getExitCodeFromMappedException(context, exception);
      if (exitCode == 0) {
         exitCode = this.getExitCodeFromExitCodeGeneratorException(exception);
      }

      return exitCode;
   }

   private int getExitCodeFromMappedException(@Nullable ConfigurableApplicationContext context, Throwable exception) {
      if (context != null && context.isActive()) {
         ExitCodeGenerators generators = new ExitCodeGenerators();
         Collection<ExitCodeExceptionMapper> beans = context.getBeansOfType(ExitCodeExceptionMapper.class).values();
         generators.addAll(exception, beans);
         return generators.getExitCode();
      } else {
         return 0;
      }
   }

   private int getExitCodeFromExitCodeGeneratorException(@Nullable Throwable exception) {
      if (exception == null) {
         return 0;
      } else {
         return exception instanceof ExitCodeGenerator generator
            ? generator.getExitCode()
            : this.getExitCodeFromExitCodeGeneratorException(exception.getCause());
      }
   }

   @Nullable SpringBootExceptionHandler getSpringBootExceptionHandler() {
      return this.isMainThread(Thread.currentThread()) ? SpringBootExceptionHandler.forCurrentThread() : null;
   }

   private boolean isMainThread(Thread currentThread) {
      return ("main".equals(currentThread.getName()) || "restartedMain".equals(currentThread.getName()))
         && "main".equals(currentThread.getThreadGroup().getName());
   }

   public @Nullable Class<?> getMainApplicationClass() {
      return this.mainApplicationClass;
   }

   public void setMainApplicationClass(@Nullable Class<?> mainApplicationClass) {
      this.mainApplicationClass = mainApplicationClass;
   }

   public @Nullable WebApplicationType getWebApplicationType() {
      return this.properties.getWebApplicationType();
   }

   public void setWebApplicationType(WebApplicationType webApplicationType) {
      Assert.notNull(webApplicationType, "'webApplicationType' must not be null");
      this.properties.setWebApplicationType(webApplicationType);
   }

   public void setAllowBeanDefinitionOverriding(boolean allowBeanDefinitionOverriding) {
      this.properties.setAllowBeanDefinitionOverriding(allowBeanDefinitionOverriding);
   }

   public void setAllowCircularReferences(boolean allowCircularReferences) {
      this.properties.setAllowCircularReferences(allowCircularReferences);
   }

   public void setLazyInitialization(boolean lazyInitialization) {
      this.properties.setLazyInitialization(lazyInitialization);
   }

   public void setHeadless(boolean headless) {
      this.headless = headless;
   }

   public void setRegisterShutdownHook(boolean registerShutdownHook) {
      this.properties.setRegisterShutdownHook(registerShutdownHook);
   }

   public void setBanner(Banner banner) {
      this.banner = banner;
   }

   public void setBannerMode(Banner.Mode bannerMode) {
      this.properties.setBannerMode(bannerMode);
   }

   public void setLogStartupInfo(boolean logStartupInfo) {
      this.properties.setLogStartupInfo(logStartupInfo);
   }

   public void setAddCommandLineProperties(boolean addCommandLineProperties) {
      this.addCommandLineProperties = addCommandLineProperties;
   }

   public void setAddConversionService(boolean addConversionService) {
      this.addConversionService = addConversionService;
   }

   public void addBootstrapRegistryInitializer(BootstrapRegistryInitializer bootstrapRegistryInitializer) {
      Assert.notNull(bootstrapRegistryInitializer, "'bootstrapRegistryInitializer' must not be null");
      this.bootstrapRegistryInitializers.addAll(Arrays.asList(bootstrapRegistryInitializer));
   }

   public void setDefaultProperties(Map<String, Object> defaultProperties) {
      this.defaultProperties = defaultProperties;
   }

   public void setDefaultProperties(Properties defaultProperties) {
      this.defaultProperties = new HashMap<>();

      for (Object key : Collections.list(defaultProperties.propertyNames())) {
         this.defaultProperties.put((String)key, defaultProperties.get(key));
      }
   }

   public void setAdditionalProfiles(String... profiles) {
      this.additionalProfiles = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(profiles)));
   }

   public Set<String> getAdditionalProfiles() {
      return this.additionalProfiles;
   }

   public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
      this.beanNameGenerator = beanNameGenerator;
   }

   public void setEnvironment(@Nullable ConfigurableEnvironment environment) {
      this.isCustomEnvironment = true;
      this.environment = environment;
   }

   public void addPrimarySources(Collection<Class<?>> additionalPrimarySources) {
      this.primarySources.addAll(additionalPrimarySources);
   }

   public Set<String> getSources() {
      return this.properties.getSources();
   }

   public void setSources(Set<String> sources) {
      Assert.notNull(sources, "'sources' must not be null");
      this.properties.setSources(sources);
   }

   public Set<Object> getAllSources() {
      Set<Object> allSources = new LinkedHashSet<>();
      if (!CollectionUtils.isEmpty(this.primarySources)) {
         allSources.addAll(this.primarySources);
      }

      if (!CollectionUtils.isEmpty(this.properties.getSources())) {
         allSources.addAll(this.properties.getSources());
      }

      return Collections.unmodifiableSet(allSources);
   }

   public void setResourceLoader(ResourceLoader resourceLoader) {
      Assert.notNull(resourceLoader, "'resourceLoader' must not be null");
      this.resourceLoader = resourceLoader;
   }

   public @Nullable String getEnvironmentPrefix() {
      return this.environmentPrefix;
   }

   public void setEnvironmentPrefix(String environmentPrefix) {
      this.environmentPrefix = environmentPrefix;
   }

   public void setApplicationContextFactory(@Nullable ApplicationContextFactory applicationContextFactory) {
      this.applicationContextFactory = applicationContextFactory != null ? applicationContextFactory : ApplicationContextFactory.DEFAULT;
   }

   public void setInitializers(Collection<? extends ApplicationContextInitializer<?>> initializers) {
      this.initializers = new ArrayList<>(initializers);
   }

   public void addInitializers(ApplicationContextInitializer<?>... initializers) {
      this.initializers.addAll(Arrays.asList(initializers));
   }

   public Set<ApplicationContextInitializer<?>> getInitializers() {
      return asUnmodifiableOrderedSet(this.initializers);
   }

   public void setListeners(Collection<? extends ApplicationListener<?>> listeners) {
      this.listeners = new ArrayList<>(listeners);
   }

   public void addListeners(ApplicationListener<?>... listeners) {
      this.listeners.addAll(Arrays.asList(listeners));
   }

   public Set<ApplicationListener<?>> getListeners() {
      return asUnmodifiableOrderedSet(this.listeners);
   }

   public void setApplicationStartup(ApplicationStartup applicationStartup) {
      this.applicationStartup = applicationStartup != null ? applicationStartup : ApplicationStartup.DEFAULT;
   }

   public ApplicationStartup getApplicationStartup() {
      return this.applicationStartup;
   }

   public boolean isKeepAlive() {
      return this.properties.isKeepAlive();
   }

   public void setKeepAlive(boolean keepAlive) {
      this.properties.setKeepAlive(keepAlive);
   }

   public static SpringApplicationShutdownHandlers getShutdownHandlers() {
      return shutdownHook.getHandlers();
   }

   public static ConfigurableApplicationContext run(Class<?> primarySource, String... args) {
      return run(new Class[]{primarySource}, args);
   }

   public static ConfigurableApplicationContext run(Class<?>[] primarySources, String[] args) {
      return new SpringApplication(primarySources).run(args);
   }

   public static void main(String[] args) throws Exception {
      run(new Class[0], args);
   }

   public static int exit(ApplicationContext context, ExitCodeGenerator... exitCodeGenerators) {
      Assert.notNull(context, "'context' must not be null");
      int exitCode = 0;

      try {
         try {
            ExitCodeGenerators generators = new ExitCodeGenerators();
            Collection<ExitCodeGenerator> beans = context.getBeansOfType(ExitCodeGenerator.class).values();
            generators.addAll(exitCodeGenerators);
            generators.addAll(beans);
            exitCode = generators.getExitCode();
            if (exitCode != 0) {
               context.publishEvent(new ExitCodeEvent(context, exitCode));
            }
         } finally {
            close(context);
         }
      } catch (Exception ex) {
         ex.printStackTrace();
         exitCode = exitCode != 0 ? exitCode : 1;
      }

      return exitCode;
   }

   public static SpringApplication.Augmented from(ThrowingConsumer<String[]> main) {
      Assert.notNull(main, "'main' must not be null");
      return new SpringApplication.Augmented(main, Collections.emptySet(), Collections.emptySet());
   }

   public static void withHook(SpringApplicationHook hook, Runnable action) {
      withHook(hook, () -> {
         action.run();
         return Void.class;
      });
   }

   public static <T> T withHook(SpringApplicationHook hook, ThrowingSupplier<T> action) {
      applicationHook.set(hook);

      try {
         return (T)action.get();
      } finally {
         applicationHook.remove();
      }
   }

   private static void close(ApplicationContext context) {
      if (context instanceof ConfigurableApplicationContext closable) {
         closable.close();
      }
   }

   private static <E> Set<E> asUnmodifiableOrderedSet(Collection<E> elements) {
      List<E> list = new ArrayList<>(elements);
      list.sort(AnnotationAwareOrderComparator.INSTANCE);
      return new LinkedHashSet<>(list);
   }

   public static class AbandonedRunException extends RuntimeException {
      private final @Nullable ConfigurableApplicationContext applicationContext;

      public AbandonedRunException() {
         this(null);
      }

      public AbandonedRunException(@Nullable ConfigurableApplicationContext applicationContext) {
         this.applicationContext = applicationContext;
      }

      public @Nullable ConfigurableApplicationContext getApplicationContext() {
         return this.applicationContext;
      }
   }

   public static class Augmented {
      private final ThrowingConsumer<String[]> main;
      private final Set<Class<?>> sources;
      private final Set<String> additionalProfiles;

      Augmented(ThrowingConsumer<String[]> main, Set<Class<?>> sources, Set<String> additionalProfiles) {
         this.main = main;
         this.sources = Set.copyOf(sources);
         this.additionalProfiles = additionalProfiles;
      }

      public SpringApplication.Augmented with(Class<?>... sources) {
         LinkedHashSet<Class<?>> merged = new LinkedHashSet<>(this.sources);
         merged.addAll(Arrays.asList(sources));
         return new SpringApplication.Augmented(this.main, merged, this.additionalProfiles);
      }

      public SpringApplication.Augmented withAdditionalProfiles(String... profiles) {
         Set<String> merged = new LinkedHashSet<>(this.additionalProfiles);
         merged.addAll(Arrays.asList(profiles));
         return new SpringApplication.Augmented(this.main, this.sources, merged);
      }

      public SpringApplication.Running run(String... args) {
         SpringApplication.Augmented.RunListener runListener = new SpringApplication.Augmented.RunListener();
         SpringApplicationHook hook = new SpringApplication.SingleUseSpringApplicationHook(springApplication -> {
            springApplication.addPrimarySources(this.sources);
            springApplication.setAdditionalProfiles(this.additionalProfiles.toArray(String[]::new));
            return runListener;
         });
         SpringApplication.withHook(hook, () -> this.main.accept(args));
         return runListener;
      }

      private static final class RunListener implements SpringApplicationRunListener, SpringApplication.Running {
         private final List<ConfigurableApplicationContext> contexts = Collections.synchronizedList(new ArrayList<>());

         @Override
         public void contextLoaded(ConfigurableApplicationContext context) {
            this.contexts.add(context);
         }

         @Override
         public ConfigurableApplicationContext getApplicationContext() {
            List<ConfigurableApplicationContext> rootContexts = this.contexts.stream().filter(context -> context.getParent() == null).toList();
            Assert.state(!rootContexts.isEmpty(), "No root application context located");
            Assert.state(rootContexts.size() == 1, "No unique root application context located");
            return rootContexts.get(0);
         }
      }
   }

   private static final class CoordinatedRestoreAtCheckpointStartup extends SpringApplication.Startup {
      private final SpringApplication.StandardStartup fallback = new SpringApplication.StandardStartup();

      @Override
      protected @Nullable Long processUptime() {
         Long uptime = CRaCMXBean.getCRaCMXBean().getUptimeSinceRestore();
         return uptime >= 0L ? uptime : this.fallback.processUptime();
      }

      @Override
      protected String action() {
         return this.restoreTime() >= 0L ? "Restored" : this.fallback.action();
      }

      private long restoreTime() {
         return CRaCMXBean.getCRaCMXBean().getRestoreTime();
      }

      @Override
      protected long startTime() {
         long restoreTime = this.restoreTime();
         return restoreTime >= 0L ? restoreTime : this.fallback.startTime();
      }
   }

   private static class FactoryAwareOrderSourceProvider implements OrderSourceProvider {
      private final ConfigurableBeanFactory beanFactory;
      private final Map<?, String> instancesToBeanNames;

      FactoryAwareOrderSourceProvider(ConfigurableBeanFactory beanFactory, Map<?, String> instancesToBeanNames) {
         this.beanFactory = beanFactory;
         this.instancesToBeanNames = instancesToBeanNames;
      }

      public @Nullable Object getOrderSource(Object obj) {
         String beanName = this.instancesToBeanNames.get(obj);
         return beanName != null ? this.getOrderSource(beanName, obj.getClass()) : null;
      }

      private @Nullable Object getOrderSource(String beanName, Class<?> instanceType) {
         try {
            RootBeanDefinition beanDefinition = (RootBeanDefinition)this.beanFactory.getMergedBeanDefinition(beanName);
            Method factoryMethod = beanDefinition.getResolvedFactoryMethod();
            Class<?> targetType = beanDefinition.getTargetType();
            targetType = targetType != instanceType ? targetType : null;
            return Stream.of(factoryMethod, targetType).filter(Objects::nonNull).toArray();
         } catch (NoSuchBeanDefinitionException ex) {
            return null;
         }
      }
   }

   private static final class KeepAlive implements ApplicationListener<ApplicationContextEvent> {
      private final AtomicReference<@Nullable Thread> thread = new AtomicReference<>();

      public void onApplicationEvent(ApplicationContextEvent event) {
         if (event instanceof ContextRefreshedEvent) {
            this.startKeepAliveThread();
         } else if (event instanceof ContextClosedEvent) {
            this.stopKeepAliveThread();
         }
      }

      private void startKeepAliveThread() {
         Thread thread = new Thread(() -> {
            while (true) {
               try {
                  Thread.sleep(Long.MAX_VALUE);
               } catch (InterruptedException ex) {
                  return;
               }
            }
         });
         if (this.thread.compareAndSet(null, thread)) {
            thread.setDaemon(false);
            thread.setName("keep-alive");
            thread.start();
         }
      }

      private void stopKeepAliveThread() {
         Thread thread = this.thread.getAndSet(null);
         if (thread != null) {
            thread.interrupt();
         }
      }
   }

   static final class NativeImageRequirementsException extends RuntimeException {
      private static final JavaVersion MINIMUM_REQUIRED_JAVA_VERSION = JavaVersion.TWENTY_FIVE;
      private static final JavaVersion CURRENT_JAVA_VERSION = JavaVersion.getJavaVersion();

      NativeImageRequirementsException(String message) {
         super(message);
      }

      static void throwIfNotMet() {
         if (CURRENT_JAVA_VERSION.isOlderThan(MINIMUM_REQUIRED_JAVA_VERSION)) {
            throw new SpringApplication.NativeImageRequirementsException(
               "Native Image requirements not met. "
                  + "Native Image must support at least Java %s but Java %s was detected".formatted(MINIMUM_REQUIRED_JAVA_VERSION, CURRENT_JAVA_VERSION)
            );
         }
      }
   }

   private static class PropertySourceOrderingBeanFactoryPostProcessor implements BeanFactoryPostProcessor, Ordered {
      private final ConfigurableApplicationContext context;

      PropertySourceOrderingBeanFactoryPostProcessor(ConfigurableApplicationContext context) {
         this.context = context;
      }

      public int getOrder() {
         return Integer.MIN_VALUE;
      }

      public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
         DefaultPropertiesPropertySource.moveToEnd(this.context.getEnvironment());
      }
   }

   public interface Running {
      ConfigurableApplicationContext getApplicationContext();
   }

   private static final class SingleUseSpringApplicationHook implements SpringApplicationHook {
      private final AtomicBoolean used = new AtomicBoolean();
      private final SpringApplicationHook delegate;

      private SingleUseSpringApplicationHook(SpringApplicationHook delegate) {
         this.delegate = delegate;
      }

      @Override
      public @Nullable SpringApplicationRunListener getRunListener(SpringApplication springApplication) {
         return this.used.compareAndSet(false, true) ? this.delegate.getRunListener(springApplication) : null;
      }
   }

   private static final class StandardStartup extends SpringApplication.Startup {
      private final Long startTime = System.currentTimeMillis();

      @Override
      protected long startTime() {
         return this.startTime;
      }

      @Override
      protected @Nullable Long processUptime() {
         try {
            return ManagementFactory.getRuntimeMXBean().getUptime();
         } catch (Throwable ex) {
            return null;
         }
      }

      @Override
      protected String action() {
         return "Started";
      }
   }

   abstract static class Startup {
      private @Nullable Duration timeTakenToStarted;

      protected abstract long startTime();

      protected abstract @Nullable Long processUptime();

      protected abstract String action();

      final Duration started() {
         long now = System.currentTimeMillis();
         this.timeTakenToStarted = Duration.ofMillis(now - this.startTime());
         return this.timeTakenToStarted;
      }

      Duration timeTakenToStarted() {
         Assert.state(this.timeTakenToStarted != null, "timeTakenToStarted is not set. Make sure to call started() before this method");
         return this.timeTakenToStarted;
      }

      private Duration ready() {
         long now = System.currentTimeMillis();
         return Duration.ofMillis(now - this.startTime());
      }

      static SpringApplication.Startup create() {
         ClassLoader classLoader = SpringApplication.Startup.class.getClassLoader();
         return ClassUtils.isPresent("jdk.crac.management.CRaCMXBean", classLoader) && ClassUtils.isPresent("org.crac.management.CRaCMXBean", classLoader)
            ? new SpringApplication.CoordinatedRestoreAtCheckpointStartup()
            : new SpringApplication.StandardStartup();
      }
   }
}
