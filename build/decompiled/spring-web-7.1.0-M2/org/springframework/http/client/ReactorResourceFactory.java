package org.springframework.http.client;

import java.time.Duration;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.SmartLifecycle;
import org.springframework.util.Assert;
import reactor.netty.http.HttpResources;
import reactor.netty.resources.ConnectionProvider;
import reactor.netty.resources.LoopResources;

public class ReactorResourceFactory implements ApplicationContextAware, InitializingBean, DisposableBean, SmartLifecycle {
   private boolean useGlobalResources = true;
   private @Nullable Consumer<HttpResources> globalResourcesConsumer;
   private Supplier<ConnectionProvider> connectionProviderSupplier = () -> ConnectionProvider.create("webflux", 500);
   private volatile @Nullable ConnectionProvider connectionProvider;
   private Supplier<LoopResources> loopResourcesSupplier = () -> LoopResources.create("webflux-http");
   private volatile @Nullable LoopResources loopResources;
   private boolean manageConnectionProvider = false;
   private boolean manageLoopResources = false;
   private Duration shutdownQuietPeriod = Duration.ofSeconds(LoopResources.DEFAULT_SHUTDOWN_QUIET_PERIOD);
   private Duration shutdownTimeout = Duration.ofSeconds(LoopResources.DEFAULT_SHUTDOWN_TIMEOUT);
   private @Nullable ApplicationContext applicationContext;
   private volatile boolean running;
   private final Object lifecycleMonitor = new Object();

   public void setUseGlobalResources(boolean useGlobalResources) {
      this.useGlobalResources = useGlobalResources;
   }

   public boolean isUseGlobalResources() {
      return this.useGlobalResources;
   }

   public void addGlobalResourcesConsumer(Consumer<HttpResources> consumer) {
      this.useGlobalResources = true;
      this.globalResourcesConsumer = this.globalResourcesConsumer != null ? this.globalResourcesConsumer.andThen(consumer) : consumer;
   }

   public void setConnectionProviderSupplier(Supplier<ConnectionProvider> supplier) {
      this.connectionProviderSupplier = supplier;
   }

   public void setConnectionProvider(ConnectionProvider connectionProvider) {
      this.connectionProvider = connectionProvider;
   }

   public ConnectionProvider getConnectionProvider() {
      if (this.connectionProvider == null) {
         this.start();
      }

      ConnectionProvider connectionProvider = this.connectionProvider;
      Assert.state(connectionProvider != null, "ConnectionProvider not initialized");
      return connectionProvider;
   }

   public void setLoopResourcesSupplier(Supplier<LoopResources> supplier) {
      this.loopResourcesSupplier = supplier;
   }

   public void setLoopResources(LoopResources loopResources) {
      this.loopResources = loopResources;
   }

   public LoopResources getLoopResources() {
      if (this.loopResources == null) {
         this.start();
      }

      LoopResources loopResources = this.loopResources;
      Assert.state(loopResources != null, "LoopResources not initialized");
      return loopResources;
   }

   public void setShutdownQuietPeriod(Duration shutdownQuietPeriod) {
      Assert.notNull(shutdownQuietPeriod, "shutdownQuietPeriod should not be null");
      this.shutdownQuietPeriod = shutdownQuietPeriod;
   }

   public void setShutdownTimeout(Duration shutdownTimeout) {
      Assert.notNull(shutdownTimeout, "shutdownTimeout should not be null");
      this.shutdownTimeout = shutdownTimeout;
   }

   public void setApplicationContext(ApplicationContext applicationContext) {
      this.applicationContext = applicationContext;
   }

   public void afterPropertiesSet() {
      if (this.applicationContext == null) {
         this.start();
      }
   }

   public void destroy() {
      if (this.applicationContext == null) {
         this.stop();
      }
   }

   public void start() {
      synchronized (this.lifecycleMonitor) {
         if (!this.running) {
            if (!this.useGlobalResources) {
               if (this.loopResources == null) {
                  this.manageLoopResources = true;
                  this.loopResources = this.loopResourcesSupplier.get();
               }

               if (this.connectionProvider == null) {
                  this.manageConnectionProvider = true;
                  this.connectionProvider = this.connectionProviderSupplier.get();
               }
            } else {
               Assert.isTrue(
                  this.loopResources == null && this.connectionProvider == null,
                  "'useGlobalResources' is mutually exclusive with explicitly configured resources"
               );
               HttpResources httpResources = HttpResources.get();
               if (this.globalResourcesConsumer != null) {
                  this.globalResourcesConsumer.accept(httpResources);
               }

               this.connectionProvider = httpResources;
               this.loopResources = httpResources;
            }

            this.running = true;
         }
      }
   }

   public void stop() {
      synchronized (this.lifecycleMonitor) {
         if (this.running) {
            if (this.useGlobalResources) {
               HttpResources.disposeLoopsAndConnectionsLater(this.shutdownQuietPeriod, this.shutdownTimeout).block();
               this.connectionProvider = null;
               this.loopResources = null;
            } else {
               try {
                  ConnectionProvider provider = this.connectionProvider;
                  if (provider != null && this.manageConnectionProvider) {
                     this.connectionProvider = null;
                     provider.disposeLater().block();
                  }
               } catch (Throwable var5) {
               }

               try {
                  LoopResources resources = this.loopResources;
                  if (resources != null && this.manageLoopResources) {
                     this.loopResources = null;
                     resources.disposeLater(this.shutdownQuietPeriod, this.shutdownTimeout).block();
                  }
               } catch (Throwable var4) {
               }
            }

            this.running = false;
         }
      }
   }

   public boolean isRunning() {
      return this.running;
   }

   public boolean isPauseable() {
      return false;
   }

   public int getPhase() {
      return 0;
   }
}
