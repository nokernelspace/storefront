package org.springframework.http.client.reactive;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;
import org.springframework.util.Assert;

public class JdkHttpClientResourceFactory implements InitializingBean, DisposableBean {
   private @Nullable Executor executor;
   private String threadPrefix = "jdk-http";

   public void setExecutor(@Nullable Executor executor) {
      this.executor = executor;
   }

   public @Nullable Executor getExecutor() {
      return this.executor;
   }

   public void setThreadPrefix(String threadPrefix) {
      Assert.notNull(threadPrefix, "Thread prefix is required");
      this.threadPrefix = threadPrefix;
   }

   public void afterPropertiesSet() throws Exception {
      if (this.executor == null) {
         String name = this.threadPrefix + "@" + Integer.toHexString(this.hashCode());
         this.executor = Executors.newCachedThreadPool(new CustomizableThreadFactory(name));
      }
   }

   public void destroy() throws Exception {
      if (this.executor instanceof ExecutorService executorService) {
         executorService.shutdown();
      }
   }
}
