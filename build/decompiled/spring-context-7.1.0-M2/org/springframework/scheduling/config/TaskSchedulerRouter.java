package org.springframework.scheduling.config;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.function.Supplier;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.EmbeddedValueResolver;
import org.springframework.beans.factory.config.NamedBeanHolder;
import org.springframework.scheduling.SchedulingAwareRunnable;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.util.StringValueResolver;
import org.springframework.util.function.SingletonSupplier;

public class TaskSchedulerRouter implements TaskScheduler, BeanNameAware, BeanFactoryAware, DisposableBean {
   public static final String DEFAULT_TASK_SCHEDULER_BEAN_NAME = "taskScheduler";
   protected static final Log logger = LogFactory.getLog(TaskSchedulerRouter.class);
   private @Nullable String beanName;
   private @Nullable BeanFactory beanFactory;
   private @Nullable StringValueResolver embeddedValueResolver;
   private final Supplier<TaskScheduler> defaultScheduler = SingletonSupplier.of(this::determineDefaultScheduler);
   private volatile @Nullable ScheduledExecutorService localExecutor;

   public void setBeanName(@Nullable String name) {
      this.beanName = name;
   }

   public void setBeanFactory(@Nullable BeanFactory beanFactory) {
      this.beanFactory = beanFactory;
      if (beanFactory instanceof ConfigurableBeanFactory configurableBeanFactory) {
         this.embeddedValueResolver = new EmbeddedValueResolver(configurableBeanFactory);
      }
   }

   @Override
   public @Nullable ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
      return this.determineTargetScheduler(task).schedule(task, trigger);
   }

   @Override
   public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
      return this.determineTargetScheduler(task).schedule(task, startTime);
   }

   @Override
   public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Instant startTime, Duration period) {
      return this.determineTargetScheduler(task).scheduleAtFixedRate(task, startTime, period);
   }

   @Override
   public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
      return this.determineTargetScheduler(task).scheduleAtFixedRate(task, period);
   }

   @Override
   public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Instant startTime, Duration delay) {
      return this.determineTargetScheduler(task).scheduleWithFixedDelay(task, startTime, delay);
   }

   @Override
   public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
      return this.determineTargetScheduler(task).scheduleWithFixedDelay(task, delay);
   }

   protected TaskScheduler determineTargetScheduler(Runnable task) {
      String qualifier = this.determineQualifier(task);
      if (this.embeddedValueResolver != null && StringUtils.hasLength(qualifier)) {
         qualifier = this.embeddedValueResolver.resolveStringValue(qualifier);
      }

      return StringUtils.hasLength(qualifier) ? this.determineQualifiedScheduler(qualifier) : this.defaultScheduler.get();
   }

   protected @Nullable String determineQualifier(Runnable task) {
      return task instanceof SchedulingAwareRunnable sar ? sar.getQualifier() : null;
   }

   protected TaskScheduler determineQualifiedScheduler(String qualifier) {
      Assert.state(this.beanFactory != null, "BeanFactory must be set to find qualified scheduler");

      try {
         return (TaskScheduler)BeanFactoryAnnotationUtils.qualifiedBeanOfType(this.beanFactory, TaskScheduler.class, qualifier);
      } catch (NoSuchBeanDefinitionException | BeanNotOfRequiredTypeException ex) {
         return new ConcurrentTaskScheduler(
            (ScheduledExecutorService)BeanFactoryAnnotationUtils.qualifiedBeanOfType(this.beanFactory, ScheduledExecutorService.class, qualifier)
         );
      }
   }

   protected TaskScheduler determineDefaultScheduler() {
      Assert.state(this.beanFactory != null, "BeanFactory must be set to find default scheduler");

      try {
         return this.resolveSchedulerBean(this.beanFactory, TaskScheduler.class, false);
      } catch (NoUniqueBeanDefinitionException ex) {
         if (logger.isTraceEnabled()) {
            logger.trace("Could not find unique TaskScheduler bean - attempting to resolve by name: " + ex.getMessage());
         }

         try {
            return this.resolveSchedulerBean(this.beanFactory, TaskScheduler.class, true);
         } catch (NoSuchBeanDefinitionException ex2) {
            if (logger.isInfoEnabled()) {
               logger.info(
                  "More than one TaskScheduler bean exists within the context, and none is named 'taskScheduler'. Mark one of them as primary or name it 'taskScheduler' (possibly as an alias); or implement the SchedulingConfigurer interface and call ScheduledTaskRegistrar#setScheduler explicitly within the configureTasks() callback: "
                     + ex.getBeanNamesFound()
               );
            }
         }
      } catch (NoSuchBeanDefinitionException ex) {
         if (logger.isTraceEnabled()) {
            logger.trace("Could not find default TaskScheduler bean - attempting to find ScheduledExecutorService: " + ex.getMessage());
         }

         try {
            return new ConcurrentTaskScheduler(this.resolveSchedulerBean(this.beanFactory, ScheduledExecutorService.class, false));
         } catch (NoUniqueBeanDefinitionException ex2) {
            if (logger.isTraceEnabled()) {
               logger.trace("Could not find unique ScheduledExecutorService bean - attempting to resolve by name: " + ex2.getMessage());
            }

            try {
               return new ConcurrentTaskScheduler(this.resolveSchedulerBean(this.beanFactory, ScheduledExecutorService.class, true));
            } catch (NoSuchBeanDefinitionException ex3) {
               if (logger.isInfoEnabled()) {
                  logger.info(
                     "More than one ScheduledExecutorService bean exists within the context, and none is named 'taskScheduler'. Mark one of them as primary or name it 'taskScheduler' (possibly as an alias); or implement the SchedulingConfigurer interface and call ScheduledTaskRegistrar#setScheduler explicitly within the configureTasks() callback: "
                        + ex2.getBeanNamesFound()
                  );
               }
            }
         } catch (NoSuchBeanDefinitionException ex2) {
            if (logger.isTraceEnabled()) {
               logger.trace("Could not find default ScheduledExecutorService bean - falling back to default: " + ex2.getMessage());
            }

            logger.info("No TaskScheduler/ScheduledExecutorService bean found for scheduled processing");
         }
      }

      ScheduledExecutorService localExecutor = Executors.newSingleThreadScheduledExecutor();
      this.localExecutor = localExecutor;
      return new ConcurrentTaskScheduler(localExecutor);
   }

   private <T> T resolveSchedulerBean(BeanFactory beanFactory, Class<T> schedulerType, boolean byName) {
      if (byName) {
         T scheduler = (T)beanFactory.getBean("taskScheduler", schedulerType);
         if (this.beanName != null && this.beanFactory instanceof ConfigurableBeanFactory cbf) {
            cbf.registerDependentBean("taskScheduler", this.beanName);
         }

         return scheduler;
      } else if (beanFactory instanceof AutowireCapableBeanFactory acbf) {
         NamedBeanHolder<T> holder = acbf.resolveNamedBean(schedulerType);
         if (this.beanName != null && beanFactory instanceof ConfigurableBeanFactory cbf) {
            cbf.registerDependentBean(holder.getBeanName(), this.beanName);
         }

         return (T)holder.getBeanInstance();
      } else {
         return (T)beanFactory.getBean(schedulerType);
      }
   }

   public void destroy() {
      ScheduledExecutorService localExecutor = this.localExecutor;
      if (localExecutor != null) {
         localExecutor.shutdownNow();
      }
   }
}
