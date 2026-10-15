package org.springframework.scheduling.annotation;

import io.micrometer.observation.ObservationRegistry;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.EmbeddedValueResolverAware;
import org.springframework.context.event.ApplicationContextEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.format.annotation.DurationFormat;
import org.springframework.format.datetime.standard.DurationFormatterUtils;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.config.CronTask;
import org.springframework.scheduling.config.FixedDelayTask;
import org.springframework.scheduling.config.FixedRateTask;
import org.springframework.scheduling.config.OneTimeTask;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.config.TaskSchedulerRouter;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.ScheduledMethodRunnable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;

public class ScheduledAnnotationBeanPostProcessor
   implements ScheduledTaskHolder,
   MergedBeanDefinitionPostProcessor,
   DestructionAwareBeanPostProcessor,
   Ordered,
   EmbeddedValueResolverAware,
   BeanNameAware,
   BeanFactoryAware,
   ApplicationContextAware,
   SmartInitializingSingleton,
   DisposableBean,
   ApplicationListener<ApplicationContextEvent> {
   public static final String DEFAULT_TASK_SCHEDULER_BEAN_NAME = "taskScheduler";
   private static final boolean REACTIVE_STREAMS_PRESENT = ClassUtils.isPresent(
      "org.reactivestreams.Publisher", ScheduledAnnotationBeanPostProcessor.class.getClassLoader()
   );
   protected final Log logger = LogFactory.getLog(this.getClass());
   private final ScheduledTaskRegistrar registrar;
   private @Nullable Object scheduler;
   private @Nullable StringValueResolver embeddedValueResolver;
   private @Nullable String beanName;
   private @Nullable BeanFactory beanFactory;
   private @Nullable ApplicationContext applicationContext;
   private @Nullable TaskSchedulerRouter localScheduler;
   private final Set<Class<?>> nonAnnotatedClasses = ConcurrentHashMap.newKeySet(64);
   private final Map<Object, Set<ScheduledTask>> scheduledTasks = new IdentityHashMap<>(16);
   private final Map<Object, List<Runnable>> reactiveSubscriptions = new IdentityHashMap<>(16);
   private final Set<Object> manualCancellationOnContextClose = Collections.newSetFromMap(new IdentityHashMap<>(16));

   public ScheduledAnnotationBeanPostProcessor() {
      this.registrar = new ScheduledTaskRegistrar();
   }

   public ScheduledAnnotationBeanPostProcessor(ScheduledTaskRegistrar registrar) {
      Assert.notNull(registrar, "ScheduledTaskRegistrar must not be null");
      this.registrar = registrar;
   }

   public int getOrder() {
      return Integer.MAX_VALUE;
   }

   public void setScheduler(Object scheduler) {
      this.scheduler = scheduler;
   }

   @Override
   public void setEmbeddedValueResolver(StringValueResolver resolver) {
      this.embeddedValueResolver = resolver;
   }

   public void setBeanName(String beanName) {
      this.beanName = beanName;
   }

   public void setBeanFactory(BeanFactory beanFactory) {
      this.beanFactory = beanFactory;
   }

   @Override
   public void setApplicationContext(ApplicationContext applicationContext) {
      this.applicationContext = applicationContext;
      if (this.beanFactory == null) {
         this.beanFactory = applicationContext;
      }
   }

   public void afterSingletonsInstantiated() {
      this.nonAnnotatedClasses.clear();
      if (this.applicationContext == null) {
         this.finishRegistration();
      }
   }

   private void finishRegistration() {
      if (this.scheduler != null) {
         this.registrar.setScheduler(this.scheduler);
      } else {
         this.localScheduler = new TaskSchedulerRouter();
         this.localScheduler.setBeanName(this.beanName);
         this.localScheduler.setBeanFactory(this.beanFactory);
         this.registrar.setTaskScheduler(this.localScheduler);
      }

      if (this.beanFactory instanceof ListableBeanFactory lbf) {
         Map<String, SchedulingConfigurer> beans = lbf.getBeansOfType(SchedulingConfigurer.class);
         List<SchedulingConfigurer> configurers = new ArrayList<>(beans.values());
         AnnotationAwareOrderComparator.sort(configurers);

         for (SchedulingConfigurer configurer : configurers) {
            configurer.configureTasks(this.registrar);
         }
      }

      this.registrar.afterPropertiesSet();
   }

   public void postProcessMergedBeanDefinition(RootBeanDefinition beanDefinition, Class<?> beanType, String beanName) {
   }

   public Object postProcessBeforeInitialization(Object bean, String beanName) {
      return bean;
   }

   public Object postProcessAfterInitialization(Object bean, String beanName) {
      if (!(bean instanceof AopInfrastructureBean) && !(bean instanceof TaskScheduler) && !(bean instanceof ScheduledExecutorService)) {
         Class<?> targetClass = AopProxyUtils.ultimateTargetClass(bean);
         if (!this.nonAnnotatedClasses.contains(targetClass) && AnnotationUtils.isCandidateClass(targetClass, List.of(Scheduled.class, Schedules.class))) {
            Map<Method, Set<Scheduled>> annotatedMethods = MethodIntrospector.selectMethods(targetClass, method -> {
               Set<Scheduled> scheduledAnnotations = AnnotatedElementUtils.getMergedRepeatableAnnotations(method, Scheduled.class, Schedules.class);
               return !scheduledAnnotations.isEmpty() ? scheduledAnnotations : null;
            });
            if (annotatedMethods.isEmpty()) {
               this.nonAnnotatedClasses.add(targetClass);
               if (this.logger.isTraceEnabled()) {
                  this.logger.trace("No @Scheduled annotations found on bean class: " + targetClass);
               }
            } else {
               annotatedMethods.forEach(
                  (method, scheduledAnnotations) -> scheduledAnnotations.forEach(scheduled -> this.processScheduled(scheduled, method, bean))
               );
               if (this.logger.isTraceEnabled()) {
                  this.logger.trace(annotatedMethods.size() + " @Scheduled methods processed on bean '" + beanName + "': " + annotatedMethods);
               }

               if (this.beanFactory != null && (!this.beanFactory.containsBean(beanName) || !this.beanFactory.isSingleton(beanName))
                  || this.beanFactory instanceof SingletonBeanRegistry sbr && sbr.containsSingleton(beanName)) {
                  this.manualCancellationOnContextClose.add(bean);
               }
            }
         }

         return bean;
      } else {
         return bean;
      }
   }

   protected void processScheduled(Scheduled scheduled, Method method, Object bean) {
      Object key = AopProxyUtils.ultimateSingletonTarget(bean);
      if (REACTIVE_STREAMS_PRESENT && ScheduledAnnotationReactiveSupport.isReactive(method)) {
         this.processScheduledAsync(scheduled, method, bean, key);
      } else {
         this.processScheduledSync(scheduled, method, bean, key);
      }
   }

   private void processScheduledSync(Scheduled scheduled, Method method, Object bean, Object key) {
      Runnable task;
      try {
         task = this.createRunnable(bean, method, scheduled.scheduler());
      } catch (IllegalArgumentException ex) {
         throw new IllegalStateException("Could not create recurring task for @Scheduled method '" + method.getName() + "': " + ex.getMessage());
      }

      this.processScheduledTask(scheduled, task, method, key);
   }

   private void processScheduledAsync(Scheduled scheduled, Method method, Object bean, Object key) {
      Runnable task;
      try {
         task = ScheduledAnnotationReactiveSupport.createSubscriptionRunnable(
            method, bean, scheduled, this::resolveObservationRegistry, this.reactiveSubscriptions.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>())
         );
      } catch (IllegalArgumentException ex) {
         throw new IllegalStateException("Could not create recurring task for @Scheduled method '" + method.getName() + "': " + ex.getMessage());
      }

      this.processScheduledTask(scheduled, task, method, key);
   }

   private void processScheduledTask(Scheduled scheduled, Runnable runnable, Method method, Object key) {
      try {
         boolean processedSchedule = false;
         String errorMessage = "Exactly one of the 'cron', 'fixedDelay' or 'fixedRate' attributes is required";
         Set<ScheduledTask> tasks = new LinkedHashSet<>(4);
         Duration initialDelay = toDuration(scheduled.initialDelay(), scheduled.timeUnit());
         String initialDelayString = scheduled.initialDelayString();
         if (StringUtils.hasText(initialDelayString)) {
            Assert.isTrue(initialDelay.isNegative(), "Specify 'initialDelay' or 'initialDelayString', not both");
            if (this.embeddedValueResolver != null) {
               initialDelayString = this.embeddedValueResolver.resolveStringValue(initialDelayString);
            }

            if (StringUtils.hasLength(initialDelayString)) {
               try {
                  initialDelay = toDuration(initialDelayString, scheduled.timeUnit());
               } catch (RuntimeException ex) {
                  throw new IllegalArgumentException("Invalid initialDelayString value \"" + initialDelayString + "\"; " + ex);
               }
            }
         }

         String cron = scheduled.cron();
         if (StringUtils.hasText(cron)) {
            String zone = scheduled.zone();
            if (this.embeddedValueResolver != null) {
               cron = this.embeddedValueResolver.resolveStringValue(cron);
               zone = this.embeddedValueResolver.resolveStringValue(zone);
            }

            if (StringUtils.hasLength(cron)) {
               Assert.isTrue(initialDelay.isNegative(), "'initialDelay' not supported for cron triggers");
               processedSchedule = true;
               if (!"-".equals(cron)) {
                  CronTrigger trigger;
                  if (StringUtils.hasText(zone)) {
                     trigger = new CronTrigger(cron, StringUtils.parseTimeZoneString(zone));
                  } else {
                     trigger = new CronTrigger(cron);
                  }

                  tasks.add(Objects.requireNonNull(this.registrar.scheduleCronTask(new CronTask(runnable, trigger))));
               }
            }
         }

         Duration delayToUse = initialDelay.isNegative() ? Duration.ZERO : initialDelay;
         Duration fixedDelay = toDuration(scheduled.fixedDelay(), scheduled.timeUnit());
         if (!fixedDelay.isNegative()) {
            Assert.isTrue(!processedSchedule, errorMessage);
            processedSchedule = true;
            tasks.add(Objects.requireNonNull(this.registrar.scheduleFixedDelayTask(new FixedDelayTask(runnable, fixedDelay, delayToUse))));
         }

         String fixedDelayString = scheduled.fixedDelayString();
         if (StringUtils.hasText(fixedDelayString)) {
            if (this.embeddedValueResolver != null) {
               fixedDelayString = this.embeddedValueResolver.resolveStringValue(fixedDelayString);
            }

            if (StringUtils.hasLength(fixedDelayString)) {
               Assert.isTrue(!processedSchedule, errorMessage);
               processedSchedule = true;

               try {
                  fixedDelay = toDuration(fixedDelayString, scheduled.timeUnit());
               } catch (RuntimeException ex) {
                  throw new IllegalArgumentException("Invalid fixedDelayString value \"" + fixedDelayString + "\"; " + ex);
               }

               tasks.add(Objects.requireNonNull(this.registrar.scheduleFixedDelayTask(new FixedDelayTask(runnable, fixedDelay, delayToUse))));
            }
         }

         Duration fixedRate = toDuration(scheduled.fixedRate(), scheduled.timeUnit());
         if (!fixedRate.isNegative()) {
            Assert.isTrue(!processedSchedule, errorMessage);
            processedSchedule = true;
            tasks.add(Objects.requireNonNull(this.registrar.scheduleFixedRateTask(new FixedRateTask(runnable, fixedRate, delayToUse))));
         }

         String fixedRateString = scheduled.fixedRateString();
         if (StringUtils.hasText(fixedRateString)) {
            if (this.embeddedValueResolver != null) {
               fixedRateString = this.embeddedValueResolver.resolveStringValue(fixedRateString);
            }

            if (StringUtils.hasLength(fixedRateString)) {
               Assert.isTrue(!processedSchedule, errorMessage);
               processedSchedule = true;

               try {
                  fixedRate = toDuration(fixedRateString, scheduled.timeUnit());
               } catch (RuntimeException ex) {
                  throw new IllegalArgumentException("Invalid fixedRateString value \"" + fixedRateString + "\"; " + ex);
               }

               tasks.add(Objects.requireNonNull(this.registrar.scheduleFixedRateTask(new FixedRateTask(runnable, fixedRate, delayToUse))));
            }
         }

         if (!processedSchedule) {
            if (initialDelay.isNegative()) {
               throw new IllegalArgumentException("One-time task only supported with specified initial delay");
            }

            tasks.add(Objects.requireNonNull(this.registrar.scheduleOneTimeTask(new OneTimeTask(runnable, delayToUse))));
         }

         synchronized (this.scheduledTasks) {
            Set<ScheduledTask> regTasks = this.scheduledTasks.computeIfAbsent(key, k -> new LinkedHashSet<>(4));
            regTasks.addAll(tasks);
         }
      } catch (IllegalArgumentException ex) {
         throw new IllegalStateException("Encountered invalid @Scheduled method '" + method.getName() + "': " + ex.getMessage());
      }
   }

   protected Runnable createRunnable(Object target, Method method, @Nullable String qualifier) {
      Runnable runnable = this.createRunnable(target, method);
      if (runnable != null) {
         return runnable;
      }

      Assert.isTrue(method.getParameterCount() == 0, "Only no-arg methods may be annotated with @Scheduled");
      Method invocableMethod = AopUtils.selectInvocableMethod(method, target.getClass());
      return new ScheduledMethodRunnable(target, invocableMethod, qualifier, this::resolveObservationRegistry);
   }

   @Deprecated(since = "6.1")
   protected @Nullable Runnable createRunnable(Object target, Method method) {
      return null;
   }

   @Override
   public Set<ScheduledTask> getScheduledTasks() {
      Set<ScheduledTask> result = new LinkedHashSet<>();
      synchronized (this.scheduledTasks) {
         for (Set<ScheduledTask> tasks : this.scheduledTasks.values()) {
            result.addAll(tasks);
         }
      }

      result.addAll(this.registrar.getScheduledTasks());
      return result;
   }

   public void postProcessBeforeDestruction(Object bean, String beanName) {
      this.cancelScheduledTasks(bean);
      this.manualCancellationOnContextClose.remove(bean);
   }

   public boolean requiresDestruction(Object bean) {
      synchronized (this.scheduledTasks) {
         return this.scheduledTasks.containsKey(bean) || this.reactiveSubscriptions.containsKey(bean);
      }
   }

   private ObservationRegistry resolveObservationRegistry() {
      return Objects.requireNonNullElse(this.registrar.getObservationRegistry(), ObservationRegistry.NOOP);
   }

   private void cancelScheduledTasks(Object bean) {
      Set<ScheduledTask> tasks;
      List<Runnable> liveSubscriptions;
      synchronized (this.scheduledTasks) {
         tasks = this.scheduledTasks.remove(bean);
         liveSubscriptions = this.reactiveSubscriptions.remove(bean);
      }

      if (tasks != null) {
         for (ScheduledTask task : tasks) {
            task.cancel(false);
         }
      }

      if (liveSubscriptions != null) {
         for (Runnable subscription : liveSubscriptions) {
            subscription.run();
         }
      }
   }

   public void destroy() {
      synchronized (this.scheduledTasks) {
         for (Set<ScheduledTask> tasks : this.scheduledTasks.values()) {
            for (ScheduledTask task : tasks) {
               task.cancel(false);
            }
         }

         this.scheduledTasks.clear();

         for (List<Runnable> liveSubscriptions : this.reactiveSubscriptions.values()) {
            for (Runnable liveSubscription : liveSubscriptions) {
               liveSubscription.run();
            }
         }

         this.reactiveSubscriptions.clear();
         this.manualCancellationOnContextClose.clear();
      }

      this.registrar.destroy();
      if (this.localScheduler != null) {
         this.localScheduler.destroy();
      }
   }

   public void onApplicationEvent(ApplicationContextEvent event) {
      if (event.getApplicationContext() == this.applicationContext) {
         if (event instanceof ContextRefreshedEvent) {
            this.finishRegistration();
         } else if (event instanceof ContextClosedEvent) {
            for (Object bean : this.manualCancellationOnContextClose) {
               this.cancelScheduledTasks(bean);
            }

            this.manualCancellationOnContextClose.clear();
         }
      }
   }

   private static Duration toDuration(long value, TimeUnit timeUnit) {
      return Duration.of(value, timeUnit.toChronoUnit());
   }

   private static Duration toDuration(String value, TimeUnit timeUnit) {
      DurationFormat.Unit unit = DurationFormat.Unit.fromChronoUnit(timeUnit.toChronoUnit());
      return DurationFormatterUtils.detectAndParse(value, unit);
   }
}
