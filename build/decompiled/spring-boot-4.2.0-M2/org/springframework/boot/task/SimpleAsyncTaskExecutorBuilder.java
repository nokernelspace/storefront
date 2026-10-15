package org.springframework.boot.task;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

public class SimpleAsyncTaskExecutorBuilder {
   private final @Nullable Boolean virtualThreads;
   private final @Nullable String threadNamePrefix;
   private final boolean cancelRemainingTasksOnClose;
   private final boolean rejectTasksWhenLimitReached;
   private final @Nullable Integer concurrencyLimit;
   private final @Nullable TaskDecorator taskDecorator;
   private final @Nullable Set<SimpleAsyncTaskExecutorCustomizer> customizers;
   private final @Nullable Duration taskTerminationTimeout;

   public SimpleAsyncTaskExecutorBuilder() {
      this(null, null, false, false, null, null, null, null);
   }

   private SimpleAsyncTaskExecutorBuilder(
      @Nullable Boolean virtualThreads,
      @Nullable String threadNamePrefix,
      boolean cancelRemainingTasksOnClose,
      boolean rejectTasksWhenLimitReached,
      @Nullable Integer concurrencyLimit,
      @Nullable TaskDecorator taskDecorator,
      @Nullable Set<SimpleAsyncTaskExecutorCustomizer> customizers,
      @Nullable Duration taskTerminationTimeout
   ) {
      this.virtualThreads = virtualThreads;
      this.threadNamePrefix = threadNamePrefix;
      this.cancelRemainingTasksOnClose = cancelRemainingTasksOnClose;
      this.rejectTasksWhenLimitReached = rejectTasksWhenLimitReached;
      this.concurrencyLimit = concurrencyLimit;
      this.taskDecorator = taskDecorator;
      this.customizers = customizers;
      this.taskTerminationTimeout = taskTerminationTimeout;
   }

   public SimpleAsyncTaskExecutorBuilder threadNamePrefix(@Nullable String threadNamePrefix) {
      return new SimpleAsyncTaskExecutorBuilder(
         this.virtualThreads,
         threadNamePrefix,
         this.cancelRemainingTasksOnClose,
         this.rejectTasksWhenLimitReached,
         this.concurrencyLimit,
         this.taskDecorator,
         this.customizers,
         this.taskTerminationTimeout
      );
   }

   public SimpleAsyncTaskExecutorBuilder virtualThreads(@Nullable Boolean virtualThreads) {
      return new SimpleAsyncTaskExecutorBuilder(
         virtualThreads,
         this.threadNamePrefix,
         this.cancelRemainingTasksOnClose,
         this.rejectTasksWhenLimitReached,
         this.concurrencyLimit,
         this.taskDecorator,
         this.customizers,
         this.taskTerminationTimeout
      );
   }

   public SimpleAsyncTaskExecutorBuilder cancelRemainingTasksOnClose(boolean cancelRemainingTasksOnClose) {
      return new SimpleAsyncTaskExecutorBuilder(
         this.virtualThreads,
         this.threadNamePrefix,
         cancelRemainingTasksOnClose,
         this.rejectTasksWhenLimitReached,
         this.concurrencyLimit,
         this.taskDecorator,
         this.customizers,
         this.taskTerminationTimeout
      );
   }

   public SimpleAsyncTaskExecutorBuilder rejectTasksWhenLimitReached(boolean rejectTasksWhenLimitReached) {
      return new SimpleAsyncTaskExecutorBuilder(
         this.virtualThreads,
         this.threadNamePrefix,
         this.cancelRemainingTasksOnClose,
         rejectTasksWhenLimitReached,
         this.concurrencyLimit,
         this.taskDecorator,
         this.customizers,
         this.taskTerminationTimeout
      );
   }

   public SimpleAsyncTaskExecutorBuilder concurrencyLimit(@Nullable Integer concurrencyLimit) {
      return new SimpleAsyncTaskExecutorBuilder(
         this.virtualThreads,
         this.threadNamePrefix,
         this.cancelRemainingTasksOnClose,
         this.rejectTasksWhenLimitReached,
         concurrencyLimit,
         this.taskDecorator,
         this.customizers,
         this.taskTerminationTimeout
      );
   }

   public SimpleAsyncTaskExecutorBuilder taskDecorator(@Nullable TaskDecorator taskDecorator) {
      return new SimpleAsyncTaskExecutorBuilder(
         this.virtualThreads,
         this.threadNamePrefix,
         this.cancelRemainingTasksOnClose,
         this.rejectTasksWhenLimitReached,
         this.concurrencyLimit,
         taskDecorator,
         this.customizers,
         this.taskTerminationTimeout
      );
   }

   public SimpleAsyncTaskExecutorBuilder taskTerminationTimeout(@Nullable Duration taskTerminationTimeout) {
      return new SimpleAsyncTaskExecutorBuilder(
         this.virtualThreads,
         this.threadNamePrefix,
         this.cancelRemainingTasksOnClose,
         this.rejectTasksWhenLimitReached,
         this.concurrencyLimit,
         this.taskDecorator,
         this.customizers,
         taskTerminationTimeout
      );
   }

   public SimpleAsyncTaskExecutorBuilder customizers(SimpleAsyncTaskExecutorCustomizer... customizers) {
      Assert.notNull(customizers, "'customizers' must not be null");
      return this.customizers(Arrays.asList(customizers));
   }

   public SimpleAsyncTaskExecutorBuilder customizers(Iterable<? extends SimpleAsyncTaskExecutorCustomizer> customizers) {
      Assert.notNull(customizers, "'customizers' must not be null");
      return new SimpleAsyncTaskExecutorBuilder(
         this.virtualThreads,
         this.threadNamePrefix,
         this.cancelRemainingTasksOnClose,
         this.rejectTasksWhenLimitReached,
         this.concurrencyLimit,
         this.taskDecorator,
         this.append(null, customizers),
         this.taskTerminationTimeout
      );
   }

   public SimpleAsyncTaskExecutorBuilder additionalCustomizers(SimpleAsyncTaskExecutorCustomizer... customizers) {
      Assert.notNull(customizers, "'customizers' must not be null");
      return this.additionalCustomizers(Arrays.asList(customizers));
   }

   public SimpleAsyncTaskExecutorBuilder additionalCustomizers(Iterable<? extends SimpleAsyncTaskExecutorCustomizer> customizers) {
      Assert.notNull(customizers, "'customizers' must not be null");
      return new SimpleAsyncTaskExecutorBuilder(
         this.virtualThreads,
         this.threadNamePrefix,
         this.cancelRemainingTasksOnClose,
         this.rejectTasksWhenLimitReached,
         this.concurrencyLimit,
         this.taskDecorator,
         this.append(this.customizers, customizers),
         this.taskTerminationTimeout
      );
   }

   public SimpleAsyncTaskExecutor build() {
      return this.configure(new SimpleAsyncTaskExecutor());
   }

   public <T extends SimpleAsyncTaskExecutor> T build(Class<T> taskExecutorClass) {
      return this.configure((T)BeanUtils.instantiateClass(taskExecutorClass));
   }

   public <T extends SimpleAsyncTaskExecutor> T configure(T taskExecutor) {
      PropertyMapper map = PropertyMapper.get();
      map.from(this.virtualThreads).to(taskExecutor::setVirtualThreads);
      map.from(this.threadNamePrefix).whenHasText().to(taskExecutor::setThreadNamePrefix);
      map.from(this.cancelRemainingTasksOnClose).to(taskExecutor::setCancelRemainingTasksOnClose);
      map.from(this.rejectTasksWhenLimitReached).to(taskExecutor::setRejectTasksWhenLimitReached);
      map.from(this.concurrencyLimit).to(taskExecutor::setConcurrencyLimit);
      map.from(this.taskDecorator).to(taskExecutor::setTaskDecorator);
      map.from(this.taskTerminationTimeout).as(Duration::toMillis).to(taskExecutor::setTaskTerminationTimeout);
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
