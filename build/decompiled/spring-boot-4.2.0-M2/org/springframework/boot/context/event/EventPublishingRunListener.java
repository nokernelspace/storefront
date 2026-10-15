package org.springframework.boot.context.event;

import java.time.Duration;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringApplicationRunListener;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.bootstrap.ConfigurableBootstrapContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.ErrorHandler;

class EventPublishingRunListener implements SpringApplicationRunListener, Ordered {
   private final SpringApplication application;
   private final String[] args;
   private final SimpleApplicationEventMulticaster initialMulticaster;

   EventPublishingRunListener(SpringApplication application, String[] args) {
      this.application = application;
      this.args = args;
      this.initialMulticaster = new SimpleApplicationEventMulticaster();
   }

   public int getOrder() {
      return 0;
   }

   @Override
   public void starting(ConfigurableBootstrapContext bootstrapContext) {
      this.multicastInitialEvent(new ApplicationStartingEvent(bootstrapContext, this.application, this.args));
   }

   @Override
   public void environmentPrepared(ConfigurableBootstrapContext bootstrapContext, ConfigurableEnvironment environment) {
      this.multicastInitialEvent(new ApplicationEnvironmentPreparedEvent(bootstrapContext, this.application, this.args, environment));
   }

   @Override
   public void contextPrepared(ConfigurableApplicationContext context) {
      this.multicastInitialEvent(new ApplicationContextInitializedEvent(this.application, this.args, context));
   }

   @Override
   public void contextLoaded(ConfigurableApplicationContext context) {
      for (ApplicationListener<?> listener : this.application.getListeners()) {
         if (listener instanceof ApplicationContextAware contextAware) {
            contextAware.setApplicationContext(context);
         }

         context.addApplicationListener(listener);
      }

      this.multicastInitialEvent(new ApplicationPreparedEvent(this.application, this.args, context));
   }

   @Override
   public void started(ConfigurableApplicationContext context, @Nullable Duration timeTaken) {
      context.publishEvent(new ApplicationStartedEvent(this.application, this.args, context, timeTaken));
      AvailabilityChangeEvent.publish(context, LivenessState.CORRECT);
   }

   @Override
   public void ready(ConfigurableApplicationContext context, @Nullable Duration timeTaken) {
      context.publishEvent(new ApplicationReadyEvent(this.application, this.args, context, timeTaken));
      AvailabilityChangeEvent.publish(context, ReadinessState.ACCEPTING_TRAFFIC);
   }

   @Override
   public void failed(@Nullable ConfigurableApplicationContext context, Throwable exception) {
      ApplicationFailedEvent event = new ApplicationFailedEvent(this.application, this.args, context, exception);
      if (context != null && context.isActive()) {
         context.publishEvent(event);
      } else {
         if (context instanceof AbstractApplicationContext abstractApplicationContext) {
            for (ApplicationListener<?> listener : abstractApplicationContext.getApplicationListeners()) {
               this.initialMulticaster.addApplicationListener(listener);
            }
         }

         this.initialMulticaster.setErrorHandler(new EventPublishingRunListener.LoggingErrorHandler());
         this.initialMulticaster.multicastEvent(event);
      }
   }

   private void multicastInitialEvent(ApplicationEvent event) {
      this.refreshApplicationListeners();
      this.initialMulticaster.multicastEvent(event);
   }

   private void refreshApplicationListeners() {
      this.application.getListeners().forEach(this.initialMulticaster::addApplicationListener);
   }

   private static final class LoggingErrorHandler implements ErrorHandler {
      private static final Log logger = LogFactory.getLog(EventPublishingRunListener.class);

      public void handleError(Throwable throwable) {
         logger.warn("Error calling ApplicationEventListener", throwable);
      }
   }
}
