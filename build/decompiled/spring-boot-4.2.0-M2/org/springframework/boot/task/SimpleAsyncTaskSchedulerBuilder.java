package org.springframework.boot.task;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

public class SimpleAsyncTaskSchedulerBuilder {
   private final @Nullable String threadNamePrefix;
   private final @Nullable Integer concurrencyLimit;
   private final @Nullable Boolean virtualThreads;
   private final @Nullable Duration taskTerminationTimeout;
   private final @Nullable TaskDecorator taskDecorator;
   private final @Nullable Set<SimpleAsyncTaskSchedulerCustomizer> customizers;

   public SimpleAsyncTaskSchedulerBuilder() {
      this(null, null, null, null, null, null);
   }

   private SimpleAsyncTaskSchedulerBuilder(
      @Nullable String threadNamePrefix,
      @Nullable Integer concurrencyLimit,
      @Nullable Boolean virtualThreads,
      @Nullable Duration taskTerminationTimeout,
      @Nullable TaskDecorator taskDecorator,
      @Nullable Set<SimpleAsyncTaskSchedulerCustomizer> taskSchedulerCustomizers
   ) {
      this.threadNamePrefix = threadNamePrefix;
      this.concurrencyLimit = concurrencyLimit;
      this.virtualThreads = virtualThreads;
      this.customizers = taskSchedulerCustomizers;
      this.taskDecorator = taskDecorator;
      this.taskTerminationTimeout = taskTerminationTimeout;
   }

   public SimpleAsyncTaskSchedulerBuilder threadNamePrefix(@Nullable String threadNamePrefix) {
      return new SimpleAsyncTaskSchedulerBuilder(
         threadNamePrefix, this.concurrencyLimit, this.virtualThreads, this.taskTerminationTimeout, this.taskDecorator, this.customizers
      );
   }

   public SimpleAsyncTaskSchedulerBuilder concurrencyLimit(@Nullable Integer concurrencyLimit) {
      return new SimpleAsyncTaskSchedulerBuilder(
         this.threadNamePrefix, concurrencyLimit, this.virtualThreads, this.taskTerminationTimeout, this.taskDecorator, this.customizers
      );
   }

   public SimpleAsyncTaskSchedulerBuilder virtualThreads(@Nullable Boolean virtualThreads) {
      return new SimpleAsyncTaskSchedulerBuilder(
         this.threadNamePrefix, this.concurrencyLimit, virtualThreads, this.taskTerminationTimeout, this.taskDecorator, this.customizers
      );
   }

   public SimpleAsyncTaskSchedulerBuilder taskTerminationTimeout(@Nullable Duration taskTerminationTimeout) {
      return new SimpleAsyncTaskSchedulerBuilder(
         this.threadNamePrefix, this.concurrencyLimit, this.virtualThreads, taskTerminationTimeout, this.taskDecorator, this.customizers
      );
   }

   public SimpleAsyncTaskSchedulerBuilder taskDecorator(@Nullable TaskDecorator taskDecorator) {
      return new SimpleAsyncTaskSchedulerBuilder(
         this.threadNamePrefix, this.concurrencyLimit, this.virtualThreads, this.taskTerminationTimeout, taskDecorator, this.customizers
      );
   }

   public SimpleAsyncTaskSchedulerBuilder customizers(SimpleAsyncTaskSchedulerCustomizer... customizers) {
      Assert.notNull(customizers, "'customizers' must not be null");
      return this.customizers(Arrays.asList(customizers));
   }

   public SimpleAsyncTaskSchedulerBuilder customizers(Iterable<? extends SimpleAsyncTaskSchedulerCustomizer> customizers) {
      Assert.notNull(customizers, "'customizers' must not be null");
      return new SimpleAsyncTaskSchedulerBuilder(
         this.threadNamePrefix, this.concurrencyLimit, this.virtualThreads, this.taskTerminationTimeout, this.taskDecorator, this.append(null, customizers)
      );
   }

   public SimpleAsyncTaskSchedulerBuilder additionalCustomizers(SimpleAsyncTaskSchedulerCustomizer... customizers) {
      Assert.notNull(customizers, "'customizers' must not be null");
      return this.additionalCustomizers(Arrays.asList(customizers));
   }

   public SimpleAsyncTaskSchedulerBuilder additionalCustomizers(Iterable<? extends SimpleAsyncTaskSchedulerCustomizer> customizers) {
      Assert.notNull(customizers, "'customizers' must not be null");
      return new SimpleAsyncTaskSchedulerBuilder(
         this.threadNamePrefix,
         this.concurrencyLimit,
         this.virtualThreads,
         this.taskTerminationTimeout,
         this.taskDecorator,
         this.append(this.customizers, customizers)
      );
   }

   public SimpleAsyncTaskScheduler build() {
      return this.configure(new SimpleAsyncTaskScheduler());
   }

   public <T extends SimpleAsyncTaskScheduler> T configure(T taskScheduler) {
      PropertyMapper map = PropertyMapper.get();
      map.from(this.threadNamePrefix).to(taskScheduler::setThreadNamePrefix);
      map.from(this.concurrencyLimit).to(taskScheduler::setConcurrencyLimit);
      map.from(this.virtualThreads).to(taskScheduler::setVirtualThreads);
      map.from(this.taskTerminationTimeout).as(Duration::toMillis).to(taskScheduler::setTaskTerminationTimeout);
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
