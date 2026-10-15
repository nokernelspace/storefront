package org.springframework.context.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.crac.CheckpointException;
import org.crac.Context;
import org.crac.Core;
import org.crac.Resource;
import org.crac.RestoreException;
import org.crac.management.CRaCMXBean;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContextException;
import org.springframework.context.Lifecycle;
import org.springframework.context.LifecycleProcessor;
import org.springframework.context.Phased;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.NativeDetector;
import org.springframework.core.SpringProperties;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

public class DefaultLifecycleProcessor implements LifecycleProcessor, BeanFactoryAware {
   public static final String CHECKPOINT_PROPERTY_NAME = "spring.context.checkpoint";
   public static final String EXIT_PROPERTY_NAME = "spring.context.exit";
   public static final String ON_REFRESH_VALUE = "onRefresh";
   private static boolean checkpointOnRefresh = "onRefresh".equalsIgnoreCase(SpringProperties.getProperty("spring.context.checkpoint"));
   private static final boolean exitOnRefresh = "onRefresh".equalsIgnoreCase(SpringProperties.getProperty("spring.context.exit"));
   private final Log logger = LogFactory.getLog(this.getClass());
   private final Map<Integer, Long> concurrentStartupForPhases = new ConcurrentHashMap<>();
   private final Map<Integer, Long> timeoutsForShutdownPhases = new ConcurrentHashMap<>();
   private volatile long timeoutPerShutdownPhase = 10000L;
   private volatile boolean running;
   private volatile @Nullable ConfigurableListableBeanFactory beanFactory;
   private volatile @Nullable Set<String> stoppedBeans;
   private @Nullable Object cracResource;

   public DefaultLifecycleProcessor() {
      if (!NativeDetector.inNativeImage() && ClassUtils.isPresent("org.crac.Core", this.getClass().getClassLoader())) {
         this.cracResource = new DefaultLifecycleProcessor.CracDelegate().registerResource();
      } else if (checkpointOnRefresh) {
         throw new IllegalStateException("Checkpoint on refresh requires a CRaC-enabled JVM and 'org.crac:crac' on the classpath");
      }
   }

   public void setConcurrentStartupForPhases(Map<Integer, Long> phasesWithTimeouts) {
      this.concurrentStartupForPhases.putAll(phasesWithTimeouts);
   }

   public void setConcurrentStartupForPhase(int phase, long timeout) {
      this.concurrentStartupForPhases.put(phase, timeout);
   }

   public void setTimeoutsForShutdownPhases(Map<Integer, Long> phasesWithTimeouts) {
      this.timeoutsForShutdownPhases.putAll(phasesWithTimeouts);
   }

   public void setTimeoutForShutdownPhase(int phase, long timeout) {
      this.timeoutsForShutdownPhases.put(phase, timeout);
   }

   public void setTimeoutPerShutdownPhase(long timeoutPerShutdownPhase) {
      this.timeoutPerShutdownPhase = timeoutPerShutdownPhase;
   }

   public void setBeanFactory(BeanFactory beanFactory) {
      if (beanFactory instanceof ConfigurableListableBeanFactory clbf) {
         if (!this.concurrentStartupForPhases.isEmpty() && clbf.getBootstrapExecutor() == null) {
            throw new IllegalStateException("'bootstrapExecutor' needs to be configured for concurrent startup");
         }

         this.beanFactory = clbf;
      } else {
         throw new IllegalArgumentException("DefaultLifecycleProcessor requires a ConfigurableListableBeanFactory: " + beanFactory);
      }
   }

   private ConfigurableListableBeanFactory getBeanFactory() {
      ConfigurableListableBeanFactory beanFactory = this.beanFactory;
      Assert.state(beanFactory != null, "No BeanFactory available");
      return beanFactory;
   }

   private Executor getBootstrapExecutor() {
      Executor executor = this.getBeanFactory().getBootstrapExecutor();
      Assert.state(executor != null, "No 'bootstrapExecutor' available");
      return executor;
   }

   private @Nullable Long determineConcurrentStartup(int phase) {
      return this.concurrentStartupForPhases.get(phase);
   }

   private long determineShutdownTimeout(int phase) {
      Long timeout = this.timeoutsForShutdownPhases.get(phase);
      return timeout != null ? timeout : this.timeoutPerShutdownPhase;
   }

   @Override
   public void start() {
      this.stoppedBeans = null;
      this.startBeans(false);
      this.running = true;
   }

   @Override
   public void stop() {
      this.stopBeans(false);
      this.running = false;
   }

   @Override
   public void onRefresh() {
      if (checkpointOnRefresh) {
         checkpointOnRefresh = false;
         new DefaultLifecycleProcessor.CracDelegate().checkpointRestore();
      }

      if (exitOnRefresh) {
         Runtime.getRuntime().halt(0);
      }

      this.stoppedBeans = null;

      try {
         this.startBeans(true);
      } catch (ApplicationContextException ex) {
         this.stopBeans(false);
         throw ex;
      }

      this.running = true;
   }

   @Override
   public void onRestart() {
      this.stoppedBeans = null;
      if (this.running) {
         this.stopBeans(true);
      }

      this.startBeans(true);
      this.running = true;
   }

   @Override
   public void onPause() {
      if (this.running) {
         this.stopBeans(true);
         this.running = false;
      }
   }

   @Override
   public void onClose() {
      this.stopBeans(false);
      this.running = false;
   }

   @Override
   public boolean isRunning() {
      return this.running;
   }

   void stopForRestart() {
      if (this.running) {
         this.stoppedBeans = ConcurrentHashMap.newKeySet();
         this.stopBeans(false);
         this.running = false;
      }
   }

   void restartAfterStop() {
      if (this.stoppedBeans != null) {
         this.startBeans(true);
         this.stoppedBeans = null;
         this.running = true;
      }
   }

   private void startBeans(boolean autoStartupOnly) {
      Map<String, Lifecycle> lifecycleBeans = this.getLifecycleBeans();
      Map<Integer, DefaultLifecycleProcessor.LifecycleGroup> phases = new TreeMap<>();
      lifecycleBeans.forEach(
         (beanName, bean) -> {
            if (!autoStartupOnly || this.isAutoStartupCandidate(beanName, bean)) {
               int startupPhase = this.getPhase(bean);
               phases.computeIfAbsent(startupPhase, phase -> new DefaultLifecycleProcessor.LifecycleGroup(phase, lifecycleBeans, autoStartupOnly, false))
                  .add(beanName, bean);
            }
         }
      );
      if (!phases.isEmpty()) {
         phases.values().forEach(DefaultLifecycleProcessor.LifecycleGroup::start);
      }
   }

   private boolean isAutoStartupCandidate(String beanName, Lifecycle bean) {
      Set<String> stoppedBeans = this.stoppedBeans;
      return stoppedBeans != null ? stoppedBeans.contains(beanName) : bean instanceof SmartLifecycle smartLifecycle && smartLifecycle.isAutoStartup();
   }

   private void doStart(Map<String, ? extends Lifecycle> lifecycleBeans, String beanName, boolean autoStartupOnly, @Nullable List<CompletableFuture<?>> futures) {
      Lifecycle bean = lifecycleBeans.remove(beanName);
      if (bean != null && bean != this) {
         String[] dependenciesForBean = this.getBeanFactory().getDependenciesForBean(beanName);

         for (String dependency : dependenciesForBean) {
            this.doStart(lifecycleBeans, dependency, autoStartupOnly, futures);
         }

         if (!bean.isRunning() && (!autoStartupOnly || this.toBeStarted(beanName, bean))) {
            if (futures != null) {
               futures.add(CompletableFuture.runAsync(() -> this.doStart(beanName, bean), this.getBootstrapExecutor()));
            } else {
               this.doStart(beanName, bean);
            }
         }
      }
   }

   private void doStart(String beanName, Lifecycle bean) {
      if (this.logger.isTraceEnabled()) {
         this.logger.trace("Starting bean '" + beanName + "' of type [" + bean.getClass().getName() + "]");
      }

      try {
         bean.start();
      } catch (Throwable ex) {
         throw new ApplicationContextException("Failed to start bean '" + beanName + "'", ex);
      }

      if (this.logger.isDebugEnabled()) {
         this.logger.debug("Successfully started bean '" + beanName + "'");
      }
   }

   private boolean toBeStarted(String beanName, Lifecycle bean) {
      Set<String> stoppedBeans = this.stoppedBeans;
      return stoppedBeans != null ? stoppedBeans.contains(beanName) : !(bean instanceof SmartLifecycle smartLifecycle && !smartLifecycle.isAutoStartup());
   }

   private void stopBeans(boolean pauseableOnly) {
      Map<String, Lifecycle> lifecycleBeans = this.getLifecycleBeans();
      Map<Integer, DefaultLifecycleProcessor.LifecycleGroup> phases = new TreeMap<>(Comparator.reverseOrder());
      lifecycleBeans.forEach(
         (beanName, bean) -> {
            int shutdownPhase = this.getPhase(bean);
            phases.computeIfAbsent(shutdownPhase, phase -> new DefaultLifecycleProcessor.LifecycleGroup(phase, lifecycleBeans, false, pauseableOnly))
               .add(beanName, bean);
         }
      );
      if (!phases.isEmpty()) {
         phases.values().forEach(DefaultLifecycleProcessor.LifecycleGroup::stop);
      }
   }

   private void doStop(
      Map<String, ? extends Lifecycle> lifecycleBeans,
      final String beanName,
      boolean pauseableOnly,
      final CountDownLatch latch,
      final Set<String> countDownBeanNames
   ) {
      Lifecycle bean = lifecycleBeans.remove(beanName);
      if (bean != null) {
         String[] dependentBeans = this.getBeanFactory().getDependentBeans(beanName);

         for (String dependentBean : dependentBeans) {
            this.doStop(lifecycleBeans, dependentBean, pauseableOnly, latch, countDownBeanNames);
         }

         try {
            if (bean.isRunning()) {
               Set<String> stoppedBeans = this.stoppedBeans;
               if (stoppedBeans != null) {
                  stoppedBeans.add(beanName);
               }

               if (bean instanceof SmartLifecycle smartLifecycle) {
                  if (pauseableOnly && !smartLifecycle.isPauseable()) {
                     latch.countDown();
                  } else {
                     if (this.logger.isTraceEnabled()) {
                        this.logger.trace("Asking bean '" + beanName + "' of type [" + bean.getClass().getName() + "] to stop");
                     }

                     countDownBeanNames.add(beanName);
                     smartLifecycle.stop(() -> {
                        latch.countDown();
                        countDownBeanNames.remove(beanName);
                        if (this.logger.isDebugEnabled()) {
                           this.logger.debug("Bean '" + beanName + "' completed its stop procedure");
                        }
                     });
                  }
               } else if (!pauseableOnly) {
                  if (this.logger.isTraceEnabled()) {
                     this.logger.trace("Stopping bean '" + beanName + "' of type [" + bean.getClass().getName() + "]");
                  }

                  bean.stop();
                  if (this.logger.isDebugEnabled()) {
                     this.logger.debug("Successfully stopped bean '" + beanName + "'");
                  }
               }
            } else if (bean instanceof SmartLifecycle) {
               latch.countDown();
            }
         } catch (Throwable ex) {
            if (this.logger.isWarnEnabled()) {
               this.logger.warn("Failed to stop bean '" + beanName + "'", ex);
            }

            if (bean instanceof SmartLifecycle) {
               latch.countDown();
            }
         }
      }
   }

   protected Map<String, Lifecycle> getLifecycleBeans() {
      ConfigurableListableBeanFactory beanFactory = this.getBeanFactory();
      Map<String, Lifecycle> beans = new LinkedHashMap<>();
      String[] beanNames = beanFactory.getBeanNamesForType(Lifecycle.class, false, false);

      for (String beanName : beanNames) {
         String beanNameToRegister = BeanFactoryUtils.transformedBeanName(beanName);
         boolean isFactoryBean = beanFactory.isFactoryBean(beanNameToRegister);
         String beanNameToCheck = isFactoryBean ? "&" + beanName : beanName;
         if (beanFactory.containsSingleton(beanNameToRegister) && (!isFactoryBean || this.matchesBeanType(Lifecycle.class, beanNameToCheck, beanFactory))
            || this.matchesBeanType(SmartLifecycle.class, beanNameToCheck, beanFactory)) {
            Object bean = beanFactory.getBean(beanNameToCheck);
            if (bean != this && bean instanceof Lifecycle lifecycle) {
               beans.put(beanNameToRegister, lifecycle);
            }
         }
      }

      return beans;
   }

   private boolean matchesBeanType(Class<?> targetType, String beanName, BeanFactory beanFactory) {
      Class<?> beanType = beanFactory.getType(beanName);
      return beanType != null && targetType.isAssignableFrom(beanType);
   }

   protected int getPhase(Lifecycle bean) {
      return bean instanceof Phased phased ? phased.getPhase() : 0;
   }

   private class CracDelegate {
      public Object registerResource() {
         DefaultLifecycleProcessor.this.logger.debug("Registering JVM checkpoint/restore callback for Spring-managed lifecycle beans");
         DefaultLifecycleProcessor.CracResourceAdapter resourceAdapter = DefaultLifecycleProcessor.this.new CracResourceAdapter();
         Core.getGlobalContext().register(resourceAdapter);
         return resourceAdapter;
      }

      public void checkpointRestore() {
         DefaultLifecycleProcessor.this.logger.info("Triggering JVM checkpoint/restore");

         try {
            Core.checkpointRestore();
         } catch (UnsupportedOperationException ex) {
            throw new ApplicationContextException("CRaC checkpoint not supported on current JVM", ex);
         } catch (CheckpointException ex) {
            throw new ApplicationContextException("Failed to take CRaC checkpoint on refresh", ex);
         } catch (RestoreException ex) {
            throw new ApplicationContextException("Failed to restore CRaC checkpoint on refresh", ex);
         }
      }
   }

   private class CracResourceAdapter implements Resource {
      private final CyclicBarrier beforeCheckpointBarrier = new CyclicBarrier(2);
      private final CyclicBarrier afterRestoreBarrier = new CyclicBarrier(2);

      public void beforeCheckpoint(Context<? extends Resource> context) {
         Thread thread = new Thread(this::preventShutdown, "prevent-shutdown");
         thread.setDaemon(false);
         thread.start();
         DefaultLifecycleProcessor.this.logger.debug("Stopping Spring-managed lifecycle beans before JVM checkpoint");
         DefaultLifecycleProcessor.this.stopForRestart();
      }

      private void preventShutdown() {
         this.awaitBarrier(this.beforeCheckpointBarrier);
         this.awaitBarrier(this.afterRestoreBarrier);
      }

      public void afterRestore(Context<? extends Resource> context) {
         this.awaitBarrier(this.beforeCheckpointBarrier);
         DefaultLifecycleProcessor.this.logger.info("Restarting Spring-managed lifecycle beans after JVM restore");
         DefaultLifecycleProcessor.this.restartAfterStop();
         this.awaitBarrier(this.afterRestoreBarrier);
         if (!DefaultLifecycleProcessor.checkpointOnRefresh) {
            DefaultLifecycleProcessor.this.logger
               .info("Spring-managed lifecycle restart completed (restored JVM running for " + CRaCMXBean.getCRaCMXBean().getUptimeSinceRestore() + " ms)");
         }
      }

      private void awaitBarrier(CyclicBarrier barrier) {
         try {
            barrier.await();
         } catch (Exception ex) {
            DefaultLifecycleProcessor.this.logger.trace("Exception from barrier", ex);
         }
      }
   }

   private class LifecycleGroup {
      private final int phase;
      private final Map<String, ? extends Lifecycle> lifecycleBeans;
      private final boolean autoStartupOnly;
      private final boolean pauseableOnly;
      private final List<DefaultLifecycleProcessor.LifecycleGroupMember> members = new ArrayList<>();
      private int smartMemberCount;

      public LifecycleGroup(int phase, Map<String, ? extends Lifecycle> lifecycleBeans, boolean autoStartupOnly, boolean pauseableOnly) {
         this.phase = phase;
         this.lifecycleBeans = lifecycleBeans;
         this.autoStartupOnly = autoStartupOnly;
         this.pauseableOnly = pauseableOnly;
      }

      public void add(String name, Lifecycle bean) {
         this.members.add(new DefaultLifecycleProcessor.LifecycleGroupMember(name, bean));
         if (bean instanceof SmartLifecycle) {
            this.smartMemberCount++;
         }
      }

      public void start() {
         if (!this.members.isEmpty()) {
            if (DefaultLifecycleProcessor.this.logger.isDebugEnabled()) {
               DefaultLifecycleProcessor.this.logger.debug("Starting beans in phase " + this.phase);
            }

            Long concurrentStartup = DefaultLifecycleProcessor.this.determineConcurrentStartup(this.phase);
            List<CompletableFuture<?>> futures = concurrentStartup != null ? new ArrayList<>() : null;

            for (DefaultLifecycleProcessor.LifecycleGroupMember member : this.members) {
               DefaultLifecycleProcessor.this.doStart(this.lifecycleBeans, member.name, this.autoStartupOnly, futures);
            }

            if (concurrentStartup != null && !CollectionUtils.isEmpty(futures)) {
               try {
                  CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(concurrentStartup, TimeUnit.MILLISECONDS);
               } catch (Exception ex) {
                  if (ex instanceof ExecutionException exEx && exEx.getCause() instanceof ApplicationContextException acEx) {
                     throw acEx;
                  }

                  throw new ApplicationContextException("Failed to start beans in phase " + this.phase + " within timeout of " + concurrentStartup + "ms", ex);
               }
            }
         }
      }

      public void stop() {
         if (!this.members.isEmpty()) {
            if (DefaultLifecycleProcessor.this.logger.isDebugEnabled()) {
               DefaultLifecycleProcessor.this.logger.debug("Stopping beans in phase " + this.phase);
            }

            CountDownLatch latch = new CountDownLatch(this.smartMemberCount);
            Set<String> countDownBeanNames = Collections.synchronizedSet(new LinkedHashSet<>());
            Set<String> lifecycleBeanNames = new HashSet<>(this.lifecycleBeans.keySet());

            for (DefaultLifecycleProcessor.LifecycleGroupMember member : this.members) {
               if (lifecycleBeanNames.contains(member.name)) {
                  DefaultLifecycleProcessor.this.doStop(this.lifecycleBeans, member.name, this.pauseableOnly, latch, countDownBeanNames);
               } else if (member.bean instanceof SmartLifecycle) {
                  latch.countDown();
               }
            }

            try {
               long shutdownTimeout = DefaultLifecycleProcessor.this.determineShutdownTimeout(this.phase);
               if (!latch.await(shutdownTimeout, TimeUnit.MILLISECONDS)
                  && !countDownBeanNames.isEmpty()
                  && DefaultLifecycleProcessor.this.logger.isInfoEnabled()) {
                  DefaultLifecycleProcessor.this.logger
                     .info(
                        "Shutdown phase "
                           + this.phase
                           + " ends with "
                           + countDownBeanNames.size()
                           + " bean"
                           + (countDownBeanNames.size() > 1 ? "s" : "")
                           + " still running after timeout of "
                           + shutdownTimeout
                           + "ms: "
                           + countDownBeanNames
                     );
               }
            } catch (InterruptedException ex) {
               Thread.currentThread().interrupt();
            }
         }
      }
   }

   private record LifecycleGroupMember(String name, Lifecycle bean) {
   }
}
