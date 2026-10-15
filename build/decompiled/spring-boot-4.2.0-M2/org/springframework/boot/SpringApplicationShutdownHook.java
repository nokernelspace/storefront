package org.springframework.boot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.util.Assert;

class SpringApplicationShutdownHook implements Runnable {
   private static final int SLEEP = 50;
   private static final long TIMEOUT = TimeUnit.MINUTES.toMillis(10L);
   private static final Log logger = LogFactory.getLog(SpringApplicationShutdownHook.class);
   private final SpringApplicationShutdownHook.Handlers handlers = new SpringApplicationShutdownHook.Handlers();
   private final Set<ConfigurableApplicationContext> contexts = new LinkedHashSet<>();
   private final Set<ConfigurableApplicationContext> closedContexts = Collections.newSetFromMap(new WeakHashMap<>());
   private final SpringApplicationShutdownHook.ApplicationContextClosedListener contextCloseListener = new SpringApplicationShutdownHook.ApplicationContextClosedListener(
      
   );
   private final AtomicBoolean shutdownHookAdded = new AtomicBoolean();
   private volatile boolean shutdownHookAdditionEnabled;
   private boolean inProgress;

   SpringApplicationShutdownHandlers getHandlers() {
      return this.handlers;
   }

   void enableShutdownHookAddition() {
      this.shutdownHookAdditionEnabled = true;
   }

   void registerApplicationContext(ConfigurableApplicationContext context) {
      this.addRuntimeShutdownHookIfNecessary();
      synchronized (SpringApplicationShutdownHook.class) {
         this.assertNotInProgress();
         context.addApplicationListener(this.contextCloseListener);
         this.contexts.add(context);
      }
   }

   private void addRuntimeShutdownHookIfNecessary() {
      if (this.shutdownHookAdditionEnabled && this.shutdownHookAdded.compareAndSet(false, true)) {
         this.addRuntimeShutdownHook();
      }
   }

   void addRuntimeShutdownHook() {
      Runtime.getRuntime().addShutdownHook(new Thread(this, "SpringApplicationShutdownHook"));
   }

   void deregisterFailedApplicationContext(ConfigurableApplicationContext applicationContext) {
      synchronized (SpringApplicationShutdownHook.class) {
         Assert.state(!applicationContext.isActive(), "Cannot unregister active application context");
         this.contexts.remove(applicationContext);
      }
   }

   @Override
   public void run() {
      Set<ConfigurableApplicationContext> contexts;
      Set<ConfigurableApplicationContext> closedContexts;
      List<SpringApplicationShutdownHook.Handler> handlers;
      synchronized (SpringApplicationShutdownHook.class) {
         this.inProgress = true;
         contexts = new LinkedHashSet<>(this.contexts);
         closedContexts = new LinkedHashSet<>(this.closedContexts);
         handlers = new ArrayList<>(this.handlers.getActions());
         Collections.reverse(handlers);
      }

      contexts.forEach(this::closeAndWait);
      closedContexts.forEach(this::closeAndWait);
      handlers.forEach(SpringApplicationShutdownHook.Handler::run);
   }

   boolean isApplicationContextRegistered(ConfigurableApplicationContext context) {
      synchronized (SpringApplicationShutdownHook.class) {
         return this.contexts.contains(context);
      }
   }

   void reset() {
      synchronized (SpringApplicationShutdownHook.class) {
         this.contexts.clear();
         this.closedContexts.clear();
         this.handlers.getActions().clear();
         this.inProgress = false;
      }
   }

   private void closeAndWait(ConfigurableApplicationContext context) {
      if (context.isActive()) {
         context.close();

         try {
            for (int waited = 0; context.isActive(); waited += 50) {
               if (waited > TIMEOUT) {
                  throw new TimeoutException();
               }

               Thread.sleep(50L);
            }
         } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            logger.warn("Interrupted waiting for application context " + context + " to become inactive");
         } catch (TimeoutException ex) {
            logger.warn("Timed out waiting for application context " + context + " to become inactive", ex);
         }
      }
   }

   private void assertNotInProgress() {
      Assert.state(!this.inProgress, "Shutdown in progress");
   }

   private final class ApplicationContextClosedListener implements ApplicationListener<ContextClosedEvent> {
      public void onApplicationEvent(ContextClosedEvent event) {
         synchronized (SpringApplicationShutdownHook.class) {
            ApplicationContext applicationContext = event.getApplicationContext();
            SpringApplicationShutdownHook.this.contexts.remove(applicationContext);
            SpringApplicationShutdownHook.this.closedContexts.add((ConfigurableApplicationContext)applicationContext);
         }
      }
   }

   record Handler(Runnable runnable) {
      @Override
      public int hashCode() {
         return System.identityHashCode(this.runnable);
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         } else {
            return obj != null && this.getClass() == obj.getClass() ? this.runnable == ((SpringApplicationShutdownHook.Handler)obj).runnable : false;
         }
      }

      void run() {
         this.runnable.run();
      }
   }

   private final class Handlers implements SpringApplicationShutdownHandlers, Runnable {
      private final Set<SpringApplicationShutdownHook.Handler> actions = new LinkedHashSet<>();

      @Override
      public void add(Runnable action) {
         Assert.notNull(action, "'action' must not be null");
         SpringApplicationShutdownHook.this.addRuntimeShutdownHookIfNecessary();
         synchronized (SpringApplicationShutdownHook.class) {
            SpringApplicationShutdownHook.this.assertNotInProgress();
            this.actions.add(new SpringApplicationShutdownHook.Handler(action));
         }
      }

      @Override
      public void remove(Runnable action) {
         Assert.notNull(action, "'action' must not be null");
         synchronized (SpringApplicationShutdownHook.class) {
            SpringApplicationShutdownHook.this.assertNotInProgress();
            this.actions.remove(new SpringApplicationShutdownHook.Handler(action));
         }
      }

      Set<SpringApplicationShutdownHook.Handler> getActions() {
         return this.actions;
      }

      @Override
      public void run() {
         SpringApplicationShutdownHook.this.run();
         SpringApplicationShutdownHook.this.reset();
      }
   }
}
