package org.springframework.boot.task;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

public class ThreadPoolTaskSchedulerBuilder {
   private final @Nullable Integer poolSize;
   private final @Nullable Boolean awaitTermination;
   private final @Nullable Duration awaitTerminationPeriod;
   private final @Nullable String threadNamePrefix;
   private final @Nullable TaskDecorator taskDecorator;
   private final @Nullable Set<ThreadPoolTaskSchedulerCustomizer> customizers;

   public ThreadPoolTaskSchedulerBuilder() {
      this(null, null, null, null, null, null);
   }

   private ThreadPoolTaskSchedulerBuilder(
      @Nullable Integer poolSize,
      @Nullable Boolean awaitTermination,
      @Nullable Duration awaitTerminationPeriod,
      @Nullable String threadNamePrefix,
      @Nullable TaskDecorator taskDecorator,
      @Nullable Set<ThreadPoolTaskSchedulerCustomizer> taskSchedulerCustomizers
   ) {
      this.poolSize = poolSize;
      this.awaitTermination = awaitTermination;
      this.awaitTerminationPeriod = awaitTerminationPeriod;
      this.threadNamePrefix = threadNamePrefix;
      this.taskDecorator = taskDecorator;
      this.customizers = taskSchedulerCustomizers;
   }

   public ThreadPoolTaskSchedulerBuilder poolSize(int poolSize) {
      return new ThreadPoolTaskSchedulerBuilder(
         poolSize, this.awaitTermination, this.awaitTerminationPeriod, this.threadNamePrefix, this.taskDecorator, this.customizers
      );
   }

   public ThreadPoolTaskSchedulerBuilder awaitTermination(boolean awaitTermination) {
      return new ThreadPoolTaskSchedulerBuilder(
         this.poolSize, awaitTermination, this.awaitTerminationPeriod, this.threadNamePrefix, this.taskDecorator, this.customizers
      );
   }

   public ThreadPoolTaskSchedulerBuilder awaitTerminationPeriod(@Nullable Duration awaitTerminationPeriod) {
      return new ThreadPoolTaskSchedulerBuilder(
         this.poolSize, this.awaitTermination, awaitTerminationPeriod, this.threadNamePrefix, this.taskDecorator, this.customizers
      );
   }

   public ThreadPoolTaskSchedulerBuilder threadNamePrefix(@Nullable String threadNamePrefix) {
      return new ThreadPoolTaskSchedulerBuilder(
         this.poolSize, this.awaitTermination, this.awaitTerminationPeriod, threadNamePrefix, this.taskDecorator, this.customizers
      );
   }

   public ThreadPoolTaskSchedulerBuilder taskDecorator(@Nullable TaskDecorator taskDecorator) {
      return new ThreadPoolTaskSchedulerBuilder(
         this.poolSize, this.awaitTermination, this.awaitTerminationPeriod, this.threadNamePrefix, taskDecorator, this.customizers
      );
   }

   public ThreadPoolTaskSchedulerBuilder customizers(ThreadPoolTaskSchedulerCustomizer... customizers) {
      Assert.notNull(customizers, "'customizers' must not be null");
      return this.customizers(Arrays.asList(customizers));
   }

   public ThreadPoolTaskSchedulerBuilder customizers(Iterable<? extends ThreadPoolTaskSchedulerCustomizer> customizers) {
      Assert.notNull(customizers, "'customizers' must not be null");
      return new ThreadPoolTaskSchedulerBuilder(
         this.poolSize, this.awaitTermination, this.awaitTerminationPeriod, this.threadNamePrefix, this.taskDecorator, this.append(null, customizers)
      );
   }

   public ThreadPoolTaskSchedulerBuilder additionalCustomizers(ThreadPoolTaskSchedulerCustomizer... customizers) {
      Assert.notNull(customizers, "'customizers' must not be null");
      return this.additionalCustomizers(Arrays.asList(customizers));
   }

   public ThreadPoolTaskSchedulerBuilder additionalCustomizers(Iterable<? extends ThreadPoolTaskSchedulerCustomizer> customizers) {
      Assert.notNull(customizers, "'customizers' must not be null");
      return new ThreadPoolTaskSchedulerBuilder(
         this.poolSize,
         this.awaitTermination,
         this.awaitTerminationPeriod,
         this.threadNamePrefix,
         this.taskDecorator,
         this.append(this.customizers, customizers)
      );
   }

   public ThreadPoolTaskScheduler build() {
      return this.configure(new ThreadPoolTaskScheduler());
   }

   public <T extends ThreadPoolTaskScheduler> T configure(T taskScheduler) {
      PropertyMapper map = PropertyMapper.get();
      map.from(this.poolSize).to(taskScheduler::setPoolSize);
      map.from(this.awaitTermination).to(taskScheduler::setWaitForTasksToCompleteOnShutdown);
      map.from(this.awaitTerminationPeriod).as(Duration::toMillis).to(taskScheduler::setAwaitTerminationMillis);
      map.from(this.threadNamePrefix).to(taskScheduler::setThreadNamePrefix);
      map.from(this.taskDecorator).to(taskScheduler::setTaskDecorator);
      if (!CollectionUtils.isEmpty(this.customizers)) {
         this.customizers.forEach(customizer -> customizer.customize(taskScheduler));
      }

      return taskScheduler;
   }

   private <T> Set<T> append(@Nullable Set<T> set, Iterable<? extends T> additions) {
      Set<T> result = new LinkedHashSet<>(set != null ? set : Collections.emptySet());
      additions.forEach(result::add);
      return Collections.unmodifiableSet(result);
   }
}
