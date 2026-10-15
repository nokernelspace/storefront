package org.springframework.context.event;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.core.ResolvableType;
import org.springframework.util.ErrorHandler;

public class SimpleApplicationEventMulticaster extends AbstractApplicationEventMulticaster {
   private @Nullable Executor taskExecutor;
   private @Nullable ErrorHandler errorHandler;
   private volatile @Nullable Log lazyLogger;

   public SimpleApplicationEventMulticaster() {
   }

   public SimpleApplicationEventMulticaster(BeanFactory beanFactory) {
      this.setBeanFactory(beanFactory);
   }

   public void setTaskExecutor(@Nullable Executor taskExecutor) {
      this.taskExecutor = taskExecutor;
   }

   protected @Nullable Executor getTaskExecutor() {
      return this.taskExecutor;
   }

   public void setErrorHandler(@Nullable ErrorHandler errorHandler) {
      this.errorHandler = errorHandler;
   }

   protected @Nullable ErrorHandler getErrorHandler() {
      return this.errorHandler;
   }

   @Override
   public void multicastEvent(ApplicationEvent event) {
      this.multicastEvent(event, null);
   }

   @Override
   public void multicastEvent(ApplicationEvent event, @Nullable ResolvableType eventType) {
      ResolvableType type = eventType != null ? eventType : ResolvableType.forInstance(event);
      Executor executor = this.getTaskExecutor();

      for (ApplicationListener<?> listener : this.getApplicationListeners(event, type)) {
         if (executor != null && listener.supportsAsyncExecution()) {
            try {
               executor.execute(() -> this.invokeListener(listener, event));
            } catch (RejectedExecutionException ex) {
               this.invokeListener(listener, event);
            }
         } else {
            this.invokeListener(listener, event);
         }
      }
   }

   protected void invokeListener(ApplicationListener<?> listener, ApplicationEvent event) {
      ErrorHandler errorHandler = this.getErrorHandler();
      if (errorHandler != null) {
         try {
            this.doInvokeListener(listener, event);
         } catch (Throwable err) {
            errorHandler.handleError(err);
         }
      } else {
         this.doInvokeListener(listener, event);
      }
   }

   private void doInvokeListener(ApplicationListener listener, ApplicationEvent event) {
      try {
         listener.onApplicationEvent(event);
      } catch (ClassCastException ex) {
         String msg = ex.getMessage();
         if (msg != null
            && !this.matchesClassCastMessage(msg, event.getClass())
            && (!(event instanceof PayloadApplicationEvent payloadEvent) || !this.matchesClassCastMessage(msg, payloadEvent.getPayload().getClass()))) {
            throw ex;
         }

         Log loggerToUse = this.lazyLogger;
         if (loggerToUse == null) {
            loggerToUse = LogFactory.getLog(this.getClass());
            this.lazyLogger = loggerToUse;
         }

         if (loggerToUse.isTraceEnabled()) {
            loggerToUse.trace("Non-matching event type for listener: " + listener, ex);
         }
      }
   }

   private boolean matchesClassCastMessage(String classCastMessage, Class<?> eventClass) {
      if (classCastMessage.startsWith(eventClass.getName())) {
         return true;
      }

      if (classCastMessage.startsWith(eventClass.toString())) {
         return true;
      }

      int moduleSeparatorIndex = classCastMessage.indexOf(47);
      return moduleSeparatorIndex != -1 && classCastMessage.startsWith(eventClass.getName(), moduleSeparatorIndex + 1);
   }
}
