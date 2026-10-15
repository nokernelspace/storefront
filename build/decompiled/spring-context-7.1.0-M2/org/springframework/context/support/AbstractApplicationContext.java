package org.springframework.context.support;

import java.io.IOException;
import java.lang.Thread.State;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.CachedIntrospectionResults;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryInitializer;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.support.ResourceEditorRegistrar;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ApplicationStartupAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.HierarchicalMessageSource;
import org.springframework.context.LifecycleProcessor;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.NoSuchMessageException;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.context.event.ApplicationEventMulticaster;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextPausedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.ContextRestartedEvent;
import org.springframework.context.event.ContextStartedEvent;
import org.springframework.context.event.ContextStoppedEvent;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.context.expression.StandardBeanExpressionResolver;
import org.springframework.context.weaving.LoadTimeWeaverAware;
import org.springframework.context.weaving.LoadTimeWeaverAwareProcessor;
import org.springframework.core.NativeDetector;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.core.metrics.ApplicationStartup;
import org.springframework.core.metrics.StartupStep;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

public abstract class AbstractApplicationContext extends DefaultResourceLoader implements ConfigurableApplicationContext {
   public static final String MESSAGE_SOURCE_BEAN_NAME = "messageSource";
   public static final String APPLICATION_EVENT_MULTICASTER_BEAN_NAME = "applicationEventMulticaster";
   public static final String LIFECYCLE_PROCESSOR_BEAN_NAME = "lifecycleProcessor";
   protected final Log logger = LogFactory.getLog(this.getClass());
   private String id = ObjectUtils.identityToString(this);
   private String displayName = ObjectUtils.identityToString(this);
   private @Nullable ApplicationContext parent;
   private @Nullable ConfigurableEnvironment environment;
   private final List<BeanFactoryPostProcessor> beanFactoryPostProcessors = new ArrayList<>();
   private long startupDate;
   private final AtomicBoolean active = new AtomicBoolean();
   private final AtomicBoolean closed = new AtomicBoolean();
   private final Lock startupShutdownLock = new ReentrantLock();
   private volatile @Nullable Thread startupShutdownThread;
   private @Nullable Thread shutdownHook;
   private final ResourcePatternResolver resourcePatternResolver;
   private @Nullable LifecycleProcessor lifecycleProcessor;
   private @Nullable MessageSource messageSource;
   private @Nullable ApplicationEventMulticaster applicationEventMulticaster;
   private ApplicationStartup applicationStartup = ApplicationStartup.DEFAULT;
   private final Set<ApplicationListener<?>> applicationListeners = new LinkedHashSet<>();
   private @Nullable Set<ApplicationListener<?>> earlyApplicationListeners;
   private @Nullable Set<ApplicationEvent> earlyApplicationEvents;

   public AbstractApplicationContext() {
      this.resourcePatternResolver = this.getResourcePatternResolver();
   }

   public AbstractApplicationContext(@Nullable ApplicationContext parent) {
      this();
      this.setParent(parent);
   }

   @Override
   public void setId(String id) {
      this.id = id;
   }

   @Override
   public String getId() {
      return this.id;
   }

   @Override
   public String getApplicationName() {
      return "";
   }

   public void setDisplayName(String displayName) {
      Assert.hasLength(displayName, "Display name must not be empty");
      this.displayName = displayName;
   }

   @Override
   public String getDisplayName() {
      return this.displayName;
   }

   @Override
   public @Nullable ApplicationContext getParent() {
      return this.parent;
   }

   @Override
   public void setEnvironment(ConfigurableEnvironment environment) {
      this.environment = environment;
   }

   @Override
   public ConfigurableEnvironment getEnvironment() {
      if (this.environment == null) {
         this.environment = this.createEnvironment();
      }

      return this.environment;
   }

   protected ConfigurableEnvironment createEnvironment() {
      return new StandardEnvironment();
   }

   @Override
   public AutowireCapableBeanFactory getAutowireCapableBeanFactory() throws IllegalStateException {
      return this.getBeanFactory();
   }

   @Override
   public long getStartupDate() {
      return this.startupDate;
   }

   @Override
   public void publishEvent(ApplicationEvent event) {
      this.publishEvent(event, null);
   }

   @Override
   public void publishEvent(Object event) {
      this.publishEvent(event, null);
   }

   protected void publishEvent(Object event, @Nullable ResolvableType typeHint) {
      Assert.notNull(event, "Event must not be null");
      ResolvableType eventType = null;
      ApplicationEvent applicationEvent;
      if (event instanceof ApplicationEvent applEvent) {
         applicationEvent = applEvent;
         eventType = typeHint;
      } else {
         ResolvableType payloadType = null;
         if (typeHint != null && ApplicationEvent.class.isAssignableFrom(typeHint.toClass())) {
            eventType = typeHint;
         } else {
            payloadType = typeHint;
         }

         applicationEvent = new PayloadApplicationEvent<>(this, event, payloadType);
      }

      if (eventType == null) {
         eventType = ResolvableType.forInstance(applicationEvent);
         if (typeHint == null) {
            typeHint = eventType;
         }
      }

      if (this.earlyApplicationEvents != null) {
         this.earlyApplicationEvents.add(applicationEvent);
      } else if (this.applicationEventMulticaster != null) {
         this.applicationEventMulticaster.multicastEvent(applicationEvent, eventType);
      }

      if (this.parent != null) {
         if (this.parent instanceof AbstractApplicationContext abstractApplicationContext) {
            abstractApplicationContext.publishEvent(event, typeHint);
         } else {
            this.parent.publishEvent(event);
         }
      }
   }

   ApplicationEventMulticaster getApplicationEventMulticaster() throws IllegalStateException {
      if (this.applicationEventMulticaster == null) {
         throw new IllegalStateException("ApplicationEventMulticaster not initialized - call 'refresh' before multicasting events via the context: " + this);
      } else {
         return this.applicationEventMulticaster;
      }
   }

   @Override
   public void setApplicationStartup(ApplicationStartup applicationStartup) {
      Assert.notNull(applicationStartup, "ApplicationStartup must not be null");
      this.applicationStartup = applicationStartup;
   }

   @Override
   public ApplicationStartup getApplicationStartup() {
      return this.applicationStartup;
   }

   LifecycleProcessor getLifecycleProcessor() throws IllegalStateException {
      if (this.lifecycleProcessor == null) {
         throw new IllegalStateException("LifecycleProcessor not initialized - call 'refresh' before invoking lifecycle methods via the context: " + this);
      } else {
         return this.lifecycleProcessor;
      }
   }

   protected ResourcePatternResolver getResourcePatternResolver() {
      return new PathMatchingResourcePatternResolver(this);
   }

   @Override
   public void setParent(@Nullable ApplicationContext parent) {
      this.parent = parent;
      if (parent != null && parent.getEnvironment() instanceof ConfigurableEnvironment configurableEnvironment) {
         this.getEnvironment().merge(configurableEnvironment);
      }
   }

   @Override
   public void addBeanFactoryPostProcessor(BeanFactoryPostProcessor postProcessor) {
      Assert.notNull(postProcessor, "BeanFactoryPostProcessor must not be null");
      this.beanFactoryPostProcessors.add(postProcessor);
   }

   public List<BeanFactoryPostProcessor> getBeanFactoryPostProcessors() {
      return this.beanFactoryPostProcessors;
   }

   @Override
   public void addApplicationListener(ApplicationListener<?> listener) {
      Assert.notNull(listener, "ApplicationListener must not be null");
      if (this.applicationEventMulticaster != null) {
         this.applicationEventMulticaster.addApplicationListener(listener);
      }

      this.applicationListeners.add(listener);
   }

   @Override
   public void removeApplicationListener(ApplicationListener<?> listener) {
      Assert.notNull(listener, "ApplicationListener must not be null");
      if (this.applicationEventMulticaster != null) {
         this.applicationEventMulticaster.removeApplicationListener(listener);
      }

      this.applicationListeners.remove(listener);
   }

   public Collection<ApplicationListener<?>> getApplicationListeners() {
      return this.applicationListeners;
   }

   @Override
   public void refresh() throws BeansException, IllegalStateException {
      this.startupShutdownLock.lock();

      try {
         this.startupShutdownThread = Thread.currentThread();
         StartupStep contextRefresh = this.applicationStartup.start("spring.context.refresh");
         this.prepareRefresh();
         ConfigurableListableBeanFactory beanFactory = this.obtainFreshBeanFactory();
         this.prepareBeanFactory(beanFactory);

         try {
            this.postProcessBeanFactory(beanFactory);
            StartupStep beanPostProcess = this.applicationStartup.start("spring.context.beans.post-process");
            this.invokeBeanFactoryPostProcessors(beanFactory);
            this.registerBeanPostProcessors(beanFactory);
            beanPostProcess.end();
            this.initMessageSource();
            this.initApplicationEventMulticaster();
            this.onRefresh();
            this.registerListeners();
            this.finishBeanFactoryInitialization(beanFactory);
            this.finishRefresh();
         } catch (RuntimeException | Error ex) {
            if (this.logger.isWarnEnabled()) {
               this.logger.warn("Exception encountered during context initialization - cancelling refresh attempt: " + ex);
            }

            if (this.lifecycleProcessor != null && this.lifecycleProcessor.isRunning()) {
               try {
                  this.lifecycleProcessor.stop();
               } catch (Throwable ex2) {
                  this.logger.warn("Exception thrown from LifecycleProcessor on cancelled refresh", ex2);
               }
            }

            this.destroyBeans();
            this.cancelRefresh(ex);
            throw ex;
         } finally {
            contextRefresh.end();
         }
      } finally {
         this.startupShutdownThread = null;
         this.startupShutdownLock.unlock();
      }
   }

   protected void prepareRefresh() {
      this.startupDate = System.currentTimeMillis();
      this.closed.set(false);
      this.active.set(true);
      if (this.logger.isDebugEnabled()) {
         if (this.logger.isTraceEnabled()) {
            this.logger.trace("Refreshing " + this);
         } else {
            this.logger.debug("Refreshing " + this.getDisplayName());
         }
      }

      this.initPropertySources();
      this.getEnvironment().validateRequiredProperties();
      if (this.earlyApplicationListeners == null) {
         this.earlyApplicationListeners = new LinkedHashSet<>(this.applicationListeners);
      } else {
         this.applicationListeners.clear();
         this.applicationListeners.addAll(this.earlyApplicationListeners);
      }

      this.earlyApplicationEvents = new LinkedHashSet<>();
   }

   protected void initPropertySources() {
   }

   protected ConfigurableListableBeanFactory obtainFreshBeanFactory() {
      this.refreshBeanFactory();
      return this.getBeanFactory();
   }

   protected void prepareBeanFactory(ConfigurableListableBeanFactory beanFactory) {
      beanFactory.setBeanClassLoader(this.getClassLoader());
      beanFactory.setBeanExpressionResolver(new StandardBeanExpressionResolver(beanFactory.getBeanClassLoader()));
      beanFactory.addPropertyEditorRegistrar(new ResourceEditorRegistrar(this, this.getEnvironment()));
      beanFactory.addBeanPostProcessor(new ApplicationContextAwareProcessor(this));
      beanFactory.ignoreDependencyInterface(EnvironmentAware.class);
      beanFactory.ignoreDependencyInterface(EmbeddedValueResolverAware.class);
      beanFactory.ignoreDependencyInterface(ResourceLoaderAware.class);
      beanFactory.ignoreDependencyInterface(ApplicationEventPublisherAware.class);
      beanFactory.ignoreDependencyInterface(MessageSourceAware.class);
      beanFactory.ignoreDependencyInterface(ApplicationContextAware.class);
      beanFactory.ignoreDependencyInterface(ApplicationStartupAware.class);
      beanFactory.registerResolvableDependency(BeanFactory.class, beanFactory);
      beanFactory.registerResolvableDependency(ResourceLoader.class, this);
      beanFactory.registerResolvableDependency(ApplicationEventPublisher.class, this);
      beanFactory.registerResolvableDependency(ApplicationContext.class, this);
      beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(this));
      if (!NativeDetector.inNativeImage() && beanFactory.containsBean("loadTimeWeaver")) {
         beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
         beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
      }

      if (!beanFactory.containsLocalBean("environment")) {
         beanFactory.registerSingleton("environment", this.getEnvironment());
      }

      if (!beanFactory.containsLocalBean("systemProperties")) {
         beanFactory.registerSingleton("systemProperties", this.getEnvironment().getSystemProperties());
      }

      if (!beanFactory.containsLocalBean("systemEnvironment")) {
         beanFactory.registerSingleton("systemEnvironment", this.getEnvironment().getSystemEnvironment());
      }

      if (!beanFactory.containsLocalBean("applicationStartup")) {
         beanFactory.registerSingleton("applicationStartup", this.getApplicationStartup());
      }
   }

   protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
   }

   protected void invokeBeanFactoryPostProcessors(ConfigurableListableBeanFactory beanFactory) {
      PostProcessorRegistrationDelegate.invokeBeanFactoryPostProcessors(beanFactory, this.getBeanFactoryPostProcessors());
      if (!NativeDetector.inNativeImage() && beanFactory.getTempClassLoader() == null && beanFactory.containsBean("loadTimeWeaver")) {
         beanFactory.addBeanPostProcessor(new LoadTimeWeaverAwareProcessor(beanFactory));
         beanFactory.setTempClassLoader(new ContextTypeMatchClassLoader(beanFactory.getBeanClassLoader()));
      }
   }

   protected void registerBeanPostProcessors(ConfigurableListableBeanFactory beanFactory) {
      PostProcessorRegistrationDelegate.registerBeanPostProcessors(beanFactory, this);
   }

   protected void initMessageSource() {
      ConfigurableListableBeanFactory beanFactory = this.getBeanFactory();
      if (beanFactory.containsLocalBean("messageSource")) {
         this.messageSource = (MessageSource)beanFactory.getBean("messageSource", MessageSource.class);
         if (this.parent != null && this.messageSource instanceof HierarchicalMessageSource hms && hms.getParentMessageSource() == null) {
            hms.setParentMessageSource(this.getInternalParentMessageSource());
         }

         if (this.logger.isTraceEnabled()) {
            this.logger.trace("Using MessageSource [" + this.messageSource + "]");
         }
      } else {
         DelegatingMessageSource dms = new DelegatingMessageSource();
         dms.setParentMessageSource(this.getInternalParentMessageSource());
         this.messageSource = dms;
         beanFactory.registerSingleton("messageSource", this.messageSource);
         if (this.logger.isTraceEnabled()) {
            this.logger.trace("No 'messageSource' bean, using [" + this.messageSource + "]");
         }
      }
   }

   protected void initApplicationEventMulticaster() {
      ConfigurableListableBeanFactory beanFactory = this.getBeanFactory();
      if (beanFactory.containsLocalBean("applicationEventMulticaster")) {
         this.applicationEventMulticaster = (ApplicationEventMulticaster)beanFactory.getBean("applicationEventMulticaster", ApplicationEventMulticaster.class);
         if (this.logger.isTraceEnabled()) {
            this.logger.trace("Using ApplicationEventMulticaster [" + this.applicationEventMulticaster + "]");
         }
      } else {
         this.applicationEventMulticaster = new SimpleApplicationEventMulticaster(beanFactory);
         beanFactory.registerSingleton("applicationEventMulticaster", this.applicationEventMulticaster);
         if (this.logger.isTraceEnabled()) {
            this.logger.trace("No 'applicationEventMulticaster' bean, using [" + this.applicationEventMulticaster.getClass().getSimpleName() + "]");
         }
      }
   }

   protected void initLifecycleProcessor() {
      ConfigurableListableBeanFactory beanFactory = this.getBeanFactory();
      if (beanFactory.containsLocalBean("lifecycleProcessor")) {
         this.lifecycleProcessor = (LifecycleProcessor)beanFactory.getBean("lifecycleProcessor", LifecycleProcessor.class);
         if (this.logger.isTraceEnabled()) {
            this.logger.trace("Using LifecycleProcessor [" + this.lifecycleProcessor + "]");
         }
      } else {
         DefaultLifecycleProcessor defaultProcessor = new DefaultLifecycleProcessor();
         defaultProcessor.setBeanFactory(beanFactory);
         this.lifecycleProcessor = defaultProcessor;
         beanFactory.registerSingleton("lifecycleProcessor", this.lifecycleProcessor);
         if (this.logger.isTraceEnabled()) {
            this.logger.trace("No 'lifecycleProcessor' bean, using [" + this.lifecycleProcessor.getClass().getSimpleName() + "]");
         }
      }
   }

   protected void onRefresh() throws BeansException {
   }

   protected void registerListeners() {
      for (ApplicationListener<?> listener : this.getApplicationListeners()) {
         this.getApplicationEventMulticaster().addApplicationListener(listener);
      }

      String[] listenerBeanNames = this.getBeanNamesForType(ApplicationListener.class, true, false);

      for (String listenerBeanName : listenerBeanNames) {
         this.getApplicationEventMulticaster().addApplicationListenerBean(listenerBeanName);
      }

      Set<ApplicationEvent> earlyEventsToProcess = this.earlyApplicationEvents;
      this.earlyApplicationEvents = null;
      if (!CollectionUtils.isEmpty(earlyEventsToProcess)) {
         for (ApplicationEvent earlyEvent : earlyEventsToProcess) {
            this.getApplicationEventMulticaster().multicastEvent(earlyEvent);
         }
      }
   }

   protected void finishBeanFactoryInitialization(ConfigurableListableBeanFactory beanFactory) {
      beanFactory.prepareSingletonBootstrap();
      if (beanFactory.containsBean("bootstrapExecutor") && beanFactory.isTypeMatch("bootstrapExecutor", Executor.class)) {
         beanFactory.setBootstrapExecutor((Executor)beanFactory.getBean("bootstrapExecutor", Executor.class));
      }

      if (beanFactory.containsBean("conversionService") && beanFactory.isTypeMatch("conversionService", ConversionService.class)) {
         beanFactory.setConversionService((ConversionService)beanFactory.getBean("conversionService", ConversionService.class));
      }

      if (!beanFactory.hasEmbeddedValueResolver()) {
         beanFactory.addEmbeddedValueResolver(strVal -> this.getEnvironment().resolvePlaceholders(strVal));
      }

      String[] initializerNames = beanFactory.getBeanNamesForType(BeanFactoryInitializer.class, false, false);

      for (String initializerName : initializerNames) {
         ((BeanFactoryInitializer)beanFactory.getBean(initializerName, BeanFactoryInitializer.class)).initialize(beanFactory);
      }

      String[] weaverAwareNames = beanFactory.getBeanNamesForType(LoadTimeWeaverAware.class, false, false);

      for (String weaverAwareName : weaverAwareNames) {
         try {
            beanFactory.getBean(weaverAwareName, LoadTimeWeaverAware.class);
         } catch (BeanNotOfRequiredTypeException ex) {
            if (this.logger.isDebugEnabled()) {
               this.logger.debug("Failed to initialize LoadTimeWeaverAware bean '" + weaverAwareName + "' due to unexpected type mismatch: " + ex.getMessage());
            }
         }
      }

      beanFactory.setTempClassLoader(null);
      beanFactory.freezeConfiguration();
      beanFactory.preInstantiateSingletons();
   }

   protected void finishRefresh() {
      this.resetCommonCaches();
      this.clearResourceCaches();
      this.initLifecycleProcessor();
      this.getLifecycleProcessor().onRefresh();
      this.publishEvent(new ContextRefreshedEvent(this));
   }

   protected void cancelRefresh(Throwable ex) {
      this.active.set(false);
      this.resetCommonCaches();
   }

   protected void resetCommonCaches() {
      ReflectionUtils.clearCache();
      AnnotationUtils.clearCache();
      ResolvableType.clearCache();
      CachedIntrospectionResults.clearClassLoader(this.getClassLoader());
   }

   public void clearResourceCaches() {
      super.clearResourceCaches();
      if (this.resourcePatternResolver instanceof PathMatchingResourcePatternResolver pmrpr) {
         pmrpr.clearCache();
      }
   }

   @Override
   public void registerShutdownHook() {
      if (this.shutdownHook == null) {
         this.shutdownHook = new Thread("SpringContextShutdownHook") {
            @Override
            public void run() {
               if (AbstractApplicationContext.this.tryLockForShutdown()) {
                  try {
                     AbstractApplicationContext.this.doClose();
                  } finally {
                     AbstractApplicationContext.this.startupShutdownLock.unlock();
                  }
               }
            }
         };
         Runtime.getRuntime().addShutdownHook(this.shutdownHook);
      }
   }

   private boolean tryLockForShutdown() {
      try {
         while (!this.startupShutdownLock.tryLock(100L, TimeUnit.MILLISECONDS)) {
            if (this.active.get() && !this.closed.get()) {
               if (!this.isStartupShutdownThreadStuck()) {
                  continue;
               }

               this.active.set(false);
               return false;
            }

            return false;
         }

         return true;
      } catch (InterruptedException ex) {
         Thread.currentThread().interrupt();
         return false;
      }
   }

   private boolean isStartupShutdownThreadStuck() {
      Thread activeThread = this.startupShutdownThread;
      if (activeThread != null && activeThread.getState() == State.WAITING) {
         activeThread.interrupt();

         try {
            Thread.sleep(10L);
         } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
         }

         if (activeThread.getState() == State.WAITING) {
            return true;
         }
      }

      return false;
   }

   @Override
   public void close() {
      if (this.tryLockForShutdown()) {
         try {
            this.startupShutdownThread = Thread.currentThread();
            this.doClose();
            if (this.shutdownHook != null) {
               try {
                  Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
               } catch (IllegalStateException var5) {
               }
            }
         } finally {
            this.startupShutdownThread = null;
            this.startupShutdownLock.unlock();
         }
      }
   }

   protected void doClose() {
      if (this.active.get() && this.closed.compareAndSet(false, true)) {
         if (this.logger.isDebugEnabled()) {
            this.logger.debug("Closing " + this);
         }

         try {
            this.publishEvent(new ContextClosedEvent(this));
         } catch (Throwable ex) {
            this.logger.warn("Exception thrown from ApplicationListener handling ContextClosedEvent", ex);
         }

         if (this.lifecycleProcessor != null) {
            try {
               this.lifecycleProcessor.onClose();
            } catch (Throwable ex) {
               this.logger.warn("Exception thrown from LifecycleProcessor on context close", ex);
            }
         }

         this.destroyBeans();
         this.closeBeanFactory();
         this.onClose();
         this.resetCommonCaches();
         if (this.earlyApplicationListeners != null) {
            this.applicationListeners.clear();
            this.applicationListeners.addAll(this.earlyApplicationListeners);
         }

         this.applicationEventMulticaster = null;
         this.messageSource = null;
         this.lifecycleProcessor = null;
         this.active.set(false);
      }
   }

   protected void destroyBeans() {
      this.getBeanFactory().destroySingletons();
   }

   protected void onClose() {
   }

   @Override
   public boolean isClosed() {
      return this.closed.get();
   }

   @Override
   public boolean isActive() {
      return this.active.get();
   }

   protected void assertBeanFactoryActive() {
      if (!this.active.get()) {
         if (this.closed.get()) {
            throw new IllegalStateException(this.getDisplayName() + " has been closed already");
         } else {
            throw new IllegalStateException(this.getDisplayName() + " has not been refreshed yet");
         }
      }
   }

   public Object getBean(String name) throws BeansException {
      this.assertBeanFactoryActive();
      return this.getBeanFactory().getBean(name);
   }

   public <T> T getBean(String name, Class<T> requiredType) throws BeansException {
      this.assertBeanFactoryActive();
      return (T)this.getBeanFactory().getBean(name, requiredType);
   }

   public <T> T getBean(String name, ParameterizedTypeReference<T> typeReference) throws BeansException {
      this.assertBeanFactoryActive();
      return (T)this.getBeanFactory().getBean(name, typeReference);
   }

   public Object getBean(String name, @Nullable Object @Nullable ... args) throws BeansException {
      this.assertBeanFactoryActive();
      return this.getBeanFactory().getBean(name, args);
   }

   public <T> T getBean(Class<T> requiredType) throws BeansException {
      this.assertBeanFactoryActive();
      return (T)this.getBeanFactory().getBean(requiredType);
   }

   public <T> T getBean(Class<T> requiredType, @Nullable Object @Nullable ... args) throws BeansException {
      this.assertBeanFactoryActive();
      return (T)this.getBeanFactory().getBean(requiredType, args);
   }

   public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType) {
      this.assertBeanFactoryActive();
      return this.getBeanFactory().getBeanProvider(requiredType);
   }

   public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType) {
      this.assertBeanFactoryActive();
      return this.getBeanFactory().getBeanProvider(requiredType);
   }

   public <T> ObjectProvider<T> getBeanProvider(ParameterizedTypeReference<T> requiredType) {
      this.assertBeanFactoryActive();
      return this.getBeanFactory().getBeanProvider(requiredType);
   }

   public boolean containsBean(String name) {
      return this.getBeanFactory().containsBean(name);
   }

   public boolean isSingleton(String name) throws NoSuchBeanDefinitionException {
      this.assertBeanFactoryActive();
      return this.getBeanFactory().isSingleton(name);
   }

   public boolean isPrototype(String name) throws NoSuchBeanDefinitionException {
      this.assertBeanFactoryActive();
      return this.getBeanFactory().isPrototype(name);
   }

   public boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException {
      this.assertBeanFactoryActive();
      return this.getBeanFactory().isTypeMatch(name, typeToMatch);
   }

   public boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException {
      this.assertBeanFactoryActive();
      return this.getBeanFactory().isTypeMatch(name, typeToMatch);
   }

   public @Nullable Class<?> getType(String name) throws NoSuchBeanDefinitionException {
      this.assertBeanFactoryActive();
      return this.getBeanFactory().getType(name);
   }

   public @Nullable Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
      this.assertBeanFactoryActive();
      return this.getBeanFactory().getType(name, allowFactoryBeanInit);
   }

   public String[] getAliases(String name) {
      return this.getBeanFactory().getAliases(name);
   }

   public boolean containsBeanDefinition(String beanName) {
      return this.getBeanFactory().containsBeanDefinition(beanName);
   }

   public int getBeanDefinitionCount() {
      return this.getBeanFactory().getBeanDefinitionCount();
   }

   public String[] getBeanDefinitionNames() {
      return this.getBeanFactory().getBeanDefinitionNames();
   }

   public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType, boolean allowEagerInit) {
      this.assertBeanFactoryActive();
      return this.getBeanFactory().getBeanProvider(requiredType, allowEagerInit);
   }

   public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType, boolean allowEagerInit) {
      this.assertBeanFactoryActive();
      return this.getBeanFactory().getBeanProvider(requiredType, allowEagerInit);
   }

   public String[] getBeanNamesForType(ResolvableType type) {
      this.assertBeanFactoryActive();
      return this.getBeanFactory().getBeanNamesForType(type);
   }

   public String[] getBeanNamesForType(ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit) {
      this.assertBeanFactoryActive();
      return this.getBeanFactory().getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
   }

   public String[] getBeanNamesForType(@Nullable Class<?> type) {
      this.assertBeanFactoryActive();
      return this.getBeanFactory().getBeanNamesForType(type);
   }

   public String[] getBeanNamesForType(@Nullable Class<?> type, boolean includeNonSingletons, boolean allowEagerInit) {
      this.assertBeanFactoryActive();
      return this.getBeanFactory().getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
   }

   public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type) throws BeansException {
      this.assertBeanFactoryActive();
      return this.getBeanFactory().getBeansOfType(type);
   }

   public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type, boolean includeNonSingletons, boolean allowEagerInit) throws BeansException {
      this.assertBeanFactoryActive();
      return this.getBeanFactory().getBeansOfType(type, includeNonSingletons, allowEagerInit);
   }

   public String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType) {
      this.assertBeanFactoryActive();
      return this.getBeanFactory().getBeanNamesForAnnotation(annotationType);
   }

   public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType) throws BeansException {
      this.assertBeanFactoryActive();
      return this.getBeanFactory().getBeansWithAnnotation(annotationType);
   }

   public <A extends Annotation> @Nullable A findAnnotationOnBean(String beanName, Class<A> annotationType) throws NoSuchBeanDefinitionException {
      this.assertBeanFactoryActive();
      return (A)this.getBeanFactory().findAnnotationOnBean(beanName, annotationType);
   }

   public <A extends Annotation> @Nullable A findAnnotationOnBean(String beanName, Class<A> annotationType, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
      this.assertBeanFactoryActive();
      return (A)this.getBeanFactory().findAnnotationOnBean(beanName, annotationType, allowFactoryBeanInit);
   }

   public <A extends Annotation> Set<A> findAllAnnotationsOnBean(String beanName, Class<A> annotationType, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
      this.assertBeanFactoryActive();
      return this.getBeanFactory().findAllAnnotationsOnBean(beanName, annotationType, allowFactoryBeanInit);
   }

   public @Nullable BeanFactory getParentBeanFactory() {
      return this.getParent();
   }

   public boolean containsLocalBean(String name) {
      return this.getBeanFactory().containsLocalBean(name);
   }

   protected @Nullable BeanFactory getInternalParentBeanFactory() {
      return (BeanFactory)(this.getParent() instanceof ConfigurableApplicationContext cac ? cac.getBeanFactory() : this.getParent());
   }

   @Override
   public @Nullable String getMessage(String code, Object @Nullable [] args, @Nullable String defaultMessage, @Nullable Locale locale) {
      return this.getMessageSource().getMessage(code, args, defaultMessage, locale);
   }

   @Override
   public String getMessage(String code, Object @Nullable [] args, @Nullable Locale locale) throws NoSuchMessageException {
      return this.getMessageSource().getMessage(code, args, locale);
   }

   @Override
   public String getMessage(MessageSourceResolvable resolvable, @Nullable Locale locale) throws NoSuchMessageException {
      return this.getMessageSource().getMessage(resolvable, locale);
   }

   private MessageSource getMessageSource() throws IllegalStateException {
      if (this.messageSource == null) {
         throw new IllegalStateException("MessageSource not initialized - call 'refresh' before accessing messages via the context: " + this);
      } else {
         return this.messageSource;
      }
   }

   protected @Nullable MessageSource getInternalParentMessageSource() {
      return this.getParent() instanceof AbstractApplicationContext abstractApplicationContext ? abstractApplicationContext.messageSource : this.getParent();
   }

   public Resource[] getResources(String locationPattern) throws IOException {
      return this.resourcePatternResolver.getResources(locationPattern);
   }

   @Override
   public void start() {
      this.getLifecycleProcessor().start();
      this.publishEvent(new ContextStartedEvent(this));
   }

   @Override
   public void stop() {
      this.getLifecycleProcessor().stop();
      this.publishEvent(new ContextStoppedEvent(this));
   }

   @Override
   public void restart() {
      this.getLifecycleProcessor().onRestart();
      this.publishEvent(new ContextRestartedEvent(this));
   }

   @Override
   public void pause() {
      this.getLifecycleProcessor().onPause();
      this.publishEvent(new ContextPausedEvent(this));
   }

   @Override
   public boolean isRunning() {
      return this.lifecycleProcessor != null && this.lifecycleProcessor.isRunning();
   }

   protected abstract void refreshBeanFactory() throws BeansException, IllegalStateException;

   protected abstract void closeBeanFactory();

   @Override
   public abstract ConfigurableListableBeanFactory getBeanFactory() throws IllegalStateException;

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder(this.getDisplayName());
      sb.append(", started on ").append(new Date(this.getStartupDate()));
      ApplicationContext parent = this.getParent();
      if (parent != null) {
         sb.append(", parent: ").append(parent.getDisplayName());
      }

      return sb.toString();
   }

   static {
      ContextClosedEvent.class.getName();
   }
}
