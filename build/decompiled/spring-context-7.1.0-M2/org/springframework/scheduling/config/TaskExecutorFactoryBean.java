package org.springframework.scheduling.config;

import java.util.concurrent.RejectedExecutionHandler;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanNameAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.StringUtils;

public class TaskExecutorFactoryBean implements FactoryBean<TaskExecutor>, BeanNameAware, InitializingBean, DisposableBean {
   private @Nullable String poolSize;
   private @Nullable Integer queueCapacity;
   private @Nullable RejectedExecutionHandler rejectedExecutionHandler;
   private @Nullable Integer keepAliveSeconds;
   private @Nullable String beanName;
   private @Nullable ThreadPoolTaskExecutor target;

   public void setPoolSize(String poolSize) {
      this.poolSize = poolSize;
   }

   public void setQueueCapacity(int queueCapacity) {
      this.queueCapacity = queueCapacity;
   }

   public void setRejectedExecutionHandler(RejectedExecutionHandler rejectedExecutionHandler) {
      this.rejectedExecutionHandler = rejectedExecutionHandler;
   }

   public void setKeepAliveSeconds(int keepAliveSeconds) {
      this.keepAliveSeconds = keepAliveSeconds;
   }

   public void setBeanName(String beanName) {
      this.beanName = beanName;
   }

   public void afterPropertiesSet() {
      ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
      this.determinePoolSizeRange(executor);
      if (this.queueCapacity != null) {
         executor.setQueueCapacity(this.queueCapacity);
      }

      if (this.keepAliveSeconds != null) {
         executor.setKeepAliveSeconds(this.keepAliveSeconds);
      }

      if (this.rejectedExecutionHandler != null) {
         executor.setRejectedExecutionHandler(this.rejectedExecutionHandler);
      }

      if (this.beanName != null) {
         executor.setThreadNamePrefix(this.beanName + "-");
      }

      executor.afterPropertiesSet();
      this.target = executor;
   }

   private void determinePoolSizeRange(ThreadPoolTaskExecutor executor) {
      if (StringUtils.hasText(this.poolSize)) {
         try {
            int separatorIndex = this.poolSize.indexOf(45);
            int corePoolSize;
            int maxPoolSize;
            if (separatorIndex != -1) {
               corePoolSize = Integer.parseInt(this.poolSize, 0, separatorIndex, 10);
               maxPoolSize = Integer.parseInt(this.poolSize, separatorIndex + 1, this.poolSize.length(), 10);
               if (corePoolSize > maxPoolSize) {
                  throw new IllegalArgumentException("Lower bound of pool-size range must not exceed the upper bound");
               }

               if (this.queueCapacity == null) {
                  if (corePoolSize != 0) {
                     throw new IllegalArgumentException("A non-zero lower bound for the size range requires a queue-capacity value");
                  }

                  executor.setAllowCoreThreadTimeOut(true);
                  corePoolSize = maxPoolSize;
               }
            } else {
               int value = Integer.parseInt(this.poolSize);
               corePoolSize = value;
               maxPoolSize = value;
            }

            executor.setCorePoolSize(corePoolSize);
            executor.setMaxPoolSize(maxPoolSize);
         } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(
               "Invalid pool-size value ["
                  + this.poolSize
                  + "]: only single maximum integer (for example, \"5\") and minimum-maximum range (for example, \"3-5\") are supported",
               ex
            );
         }
      }
   }

   public @Nullable TaskExecutor getObject() {
      return this.target;
   }

   public Class<? extends TaskExecutor> getObjectType() {
      return (Class<? extends TaskExecutor>)(this.target != null ? this.target.getClass() : ThreadPoolTaskExecutor.class);
   }

   public boolean isSingleton() {
      return true;
   }

   public void destroy() {
      if (this.target != null) {
         this.target.destroy();
      }
   }
}
