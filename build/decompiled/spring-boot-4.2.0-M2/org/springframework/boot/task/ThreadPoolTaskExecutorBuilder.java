package org.springframework.boot.task;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

public class ThreadPoolTaskExecutorBuilder {
   private final @Nullable Integer queueCapacity;
   private final @Nullable Integer corePoolSize;
   private final @Nullable Integer maxPoolSize;
   private final @Nullable Boolean allowCoreThreadTimeOut;
   private final @Nullable Duration keepAlive;
   private final @Nullable Boolean acceptTasksAfterContextClose;
   private final @Nullable Boolean awaitTermination;
   private final @Nullable Duration awaitTerminationPeriod;
   private final @Nullable String threadNamePrefix;
   private final @Nullable TaskDecorator taskDecorator;
   private final @Nullable Set<ThreadPoolTaskExecutorCustomizer> customizers;

   public ThreadPoolTaskExecutorBuilder() {
      this.queueCapacity = null;
      this.corePoolSize = null;
      this.maxPoolSize = null;
      this.allowCoreThreadTimeOut = null;
      this.keepAlive = null;
      this.acceptTasksAfterContextClose = null;
      this.awaitTermination = null;
      this.awaitTerminationPeriod = null;
      this.threadNamePrefix = null;
      this.taskDecorator = null;
      this.customizers = null;
   }

   private ThreadPoolTaskExecutorBuilder(
      @Nullable Integer queueCapacity,
      @Nullable Integer corePoolSize,
      @Nullable Integer maxPoolSize,
      @Nullable Boolean allowCoreThreadTimeOut,
      @Nullable Duration keepAlive,
      @Nullable Boolean acceptTasksAfterContextClose,
      @Nullable Boolean awaitTermination,
      @Nullable Duration awaitTerminationPeriod,
      @Nullable String threadNamePrefix,
      @Nullable TaskDecorator taskDecorator,
      @Nullable Set<ThreadPoolTaskExecutorCustomizer> customizers
   ) {
      this.queueCapacity = queueCapacity;
      this.corePoolSize = corePoolSize;
      this.maxPoolSize = maxPoolSize;
      this.allowCoreThreadTimeOut = allowCoreThreadTimeOut;
      this.keepAlive = keepAlive;
      this.acceptTasksAfterContextClose = acceptTasksAfterContextClose;
      this.awaitTermination = awaitTermination;
      this.awaitTerminationPeriod = awaitTerminationPeriod;
      this.threadNamePrefix = threadNamePrefix;
      this.taskDecorator = taskDecorator;
      this.customizers = customizers;
   }

   public ThreadPoolTaskExecutorBuilder queueCapacity(int queueCapacity) {
      return new ThreadPoolTaskExecutorBuilder(
         queueCapacity,
         this.corePoolSize,
         this.maxPoolSize,
         this.allowCoreThreadTimeOut,
         this.keepAlive,
         this.acceptTasksAfterContextClose,
         this.awaitTermination,
         this.awaitTerminationPeriod,
         this.threadNamePrefix,
         this.taskDecorator,
         this.customizers
      );
   }

   public ThreadPoolTaskExecutorBuilder corePoolSize(int corePoolSize) {
      return new ThreadPoolTaskExecutorBuilder(
         this.queueCapacity,
         corePoolSize,
         this.maxPoolSize,
         this.allowCoreThreadTimeOut,
         this.keepAlive,
         this.acceptTasksAfterContextClose,
         this.awaitTermination,
         this.awaitTerminationPeriod,
         this.threadNamePrefix,
         this.taskDecorator,
         this.customizers
      );
   }

   public ThreadPoolTaskExecutorBuilder maxPoolSize(int maxPoolSize) {
      return new ThreadPoolTaskExecutorBuilder(
         this.queueCapacity,
         this.corePoolSize,
         maxPoolSize,
         this.allowCoreThreadTimeOut,
         this.keepAlive,
         this.acceptTasksAfterContextClose,
         this.awaitTermination,
         this.awaitTerminationPeriod,
         this.threadNamePrefix,
         this.taskDecorator,
         this.customizers
      );
   }

   public ThreadPoolTaskExecutorBuilder allowCoreThreadTimeOut(boolean allowCoreThreadTimeOut) {
      return new ThreadPoolTaskExecutorBuilder(
         this.queueCapacity,
         this.corePoolSize,
         this.maxPoolSize,
         allowCoreThreadTimeOut,
         this.keepAlive,
         this.acceptTasksAfterContextClose,
         this.awaitTermination,
         this.awaitTerminationPeriod,
         this.threadNamePrefix,
         this.taskDecorator,
         this.customizers
      );
   }

   public ThreadPoolTaskExecutorBuilder keepAlive(@Nullable Duration keepAlive) {
      return new ThreadPoolTaskExecutorBuilder(
         this.queueCapacity,
         this.corePoolSize,
         this.maxPoolSize,
         this.allowCoreThreadTimeOut,
         keepAlive,
         this.acceptTasksAfterContextClose,
         this.awaitTermination,
         this.awaitTerminationPeriod,
         this.threadNamePrefix,
         this.taskDecorator,
         this.customizers
      );
   }

   public ThreadPoolTaskExecutorBuilder acceptTasksAfterContextClose(boolean acceptTasksAfterContextClose) {
      return new ThreadPoolTaskExecutorBuilder(
         this.queueCapacity,
         this.corePoolSize,
         this.maxPoolSize,
         this.allowCoreThreadTimeOut,
         this.keepAlive,
         acceptTasksAfterContextClose,
         this.awaitTermination,
         this.awaitTerminationPeriod,
         this.threadNamePrefix,
         this.taskDecorator,
         this.customizers
      );
   }

   public ThreadPoolTaskExecutorBuilder awaitTermination(boolean awaitTermination) {
      return new ThreadPoolTaskExecutorBuilder(
         this.queueCapacity,
         this.corePoolSize,
         this.maxPoolSize,
         this.allowCoreThreadTimeOut,
         this.keepAlive,
         this.acceptTasksAfterContextClose,
         awaitTermination,
         this.awaitTerminationPeriod,
         this.threadNamePrefix,
         this.taskDecorator,
         this.customizers
      );
   }

   public ThreadPoolTaskExecutorBuilder awaitTerminationPeriod(@Nullable Duration awaitTerminationPeriod) {
      return new ThreadPoolTaskExecutorBuilder(
         this.queueCapacity,
         this.corePoolSize,
         this.maxPoolSize,
         this.allowCoreThreadTimeOut,
         this.keepAlive,
         this.acceptTasksAfterContextClose,
         this.awaitTermination,
         awaitTerminationPeriod,
         this.threadNamePrefix,
         this.taskDecorator,
         this.customizers
      );
   }

   public ThreadPoolTaskExecutorBuilder threadNamePrefix(@Nullable String threadNamePrefix) {
      return new ThreadPoolTaskExecutorBuilder(
         this.queueCapacity,
         this.corePoolSize,
         this.maxPoolSize,
         this.allowCoreThreadTimeOut,
         this.keepAlive,
         this.acceptTasksAfterContextClose,
         this.awaitTermination,
         this.awaitTerminationPeriod,
         threadNamePrefix,
         this.taskDecorator,
         this.customizers
      );
   }

   public ThreadPoolTaskExecutorBuilder taskDecorator(@Nullable TaskDecorator taskDecorator) {
      return new ThreadPoolTaskExecutorBuilder(
         this.queueCapacity,
         this.corePoolSize,
         this.maxPoolSize,
         this.allowCoreThreadTimeOut,
         this.keepAlive,
         this.acceptTasksAfterContextClose,
         this.awaitTermination,
         this.awaitTerminationPeriod,
         this.threadNamePrefix,
         taskDecorator,
         this.customizers
      );
   }

   public ThreadPoolTaskExecutorBuilder customizers(ThreadPoolTaskExecutorCustomizer... customizers) {
      Assert.notNull(customizers, "'customizers' must not be null");
      return this.customizers(Arrays.asList(customizers));
   }

   public ThreadPoolTaskExecutorBuilder customizers(Iterable<? extends ThreadPoolTaskExecutorCustomizer> customizers) {
      Assert.notNull(customizers, "'customizers' must not be null");
      return new ThreadPoolTaskExecutorBuilder(
         this.queueCapacity,
         this.corePoolSize,
         this.maxPoolSize,
         this.allowCoreThreadTimeOut,
         this.keepAlive,
         this.acceptTasksAfterContextClose,
         this.awaitTermination,
         this.awaitTerminationPeriod,
         this.threadNamePrefix,
         this.taskDecorator,
         this.append(null, customizers)
      );
   }

   public ThreadPoolTaskExecutorBuilder additionalCustomizers(ThreadPoolTaskExecutorCustomizer... customizers) {
      Assert.notNull(customizers, "'customizers' must not be null");
      return this.additionalCustomizers(Arrays.asList(customizers));
   }

   public ThreadPoolTaskExecutorBuilder additionalCustomizers(Iterable<? extends ThreadPoolTaskExecutorCustomizer> customizers) {
      Assert.notNull(customizers, "'customizers' must not be null");
      return new ThreadPoolTaskExecutorBuilder(
         this.queueCapacity,
         this.corePoolSize,
         this.maxPoolSize,
         this.allowCoreThreadTimeOut,
         this.keepAlive,
         this.acceptTasksAfterContextClose,
         this.awaitTermination,
         this.awaitTerminationPeriod,
         this.threadNamePrefix,
         this.taskDecorator,
         this.append(this.customizers, customizers)
      );
   }

   public ThreadPoolTaskExecutor build() {
      return this.configure(new ThreadPoolTaskExecutor());
   }

   public <T extends ThreadPoolTaskExecutor> T build(Class<T> taskExecutorClass) {
      return this.configure((T)BeanUtils.instantiateClass(taskExecutorClass));
   }

   public <T extends ThreadPoolTaskExecutor> T configure(T taskExecutor) {
      PropertyMapper map = PropertyMapper.get();
      map.from(this.queueCapacity).to(taskExecutor::setQueueCapacity);
      map.from(this.corePoolSize).to(taskExecutor::setCorePoolSize);
      map.from(this.maxPoolSize).to(taskExecutor::setMaxPoolSize);
      map.from(this.keepAlive).asInt(Duration::getSeconds).to(taskExecutor::setKeepAliveSeconds);
      map.from(this.allowCoreThreadTimeOut).to(taskExecutor::setAllowCoreThreadTimeOut);
      map.from(this.acceptTasksAfterContextClose).to(taskExecutor::setAcceptTasksAfterContextClose);
      map.from(this.awaitTermination).to(taskExecutor::setWaitForTasksToCompleteOnShutdown);
      map.from(this.awaitTerminationPeriod).as(Duration::toMillis).to(taskExecutor::setAwaitTerminationMillis);
      map.from(this.threadNamePrefix).whenHasText().to(taskExecutor::setThreadNamePrefix);
      map.from(this.taskDecorator).to(taskExecutor::setTaskDecorator);
      if (!CollectionUtils.isEmpty(this.customizers)) {
         this.customizers.forEach(customizer -> customizer.customize(taskExecutor));
      }

      return taskExecutor;
   }

   private <T> Set<T> append(@Nullable Set<T> set, Iterable<? extends T> additions) {
      Set<T> result = new LinkedHashSet<>(set != null ? set : Collections.emptySet());
      additions.forEach(result::add);
      return Collections.unmodifiableSet(result);
   }
}
