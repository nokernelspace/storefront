package org.springframework.context.event;

import java.lang.reflect.Constructor;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.util.Assert;

public class EventPublicationInterceptor implements MethodInterceptor, ApplicationEventPublisherAware, InitializingBean {
   private EventPublicationInterceptor.ApplicationEventFactory applicationEventFactory = (invocation, returnValue) -> null;
   private @Nullable ApplicationEventPublisher applicationEventPublisher;

   public void setApplicationEventClass(Class<? extends ApplicationEvent> applicationEventClass) {
      if (ApplicationEvent.class != applicationEventClass && ApplicationEvent.class.isAssignableFrom(applicationEventClass)) {
         try {
            Constructor<? extends ApplicationEvent> ctor = applicationEventClass.getConstructor(Object.class);
            this.applicationEventFactory = (invocation, returnValue) -> (ApplicationEvent)BeanUtils.instantiateClass(ctor, new Object[]{invocation.getThis()});
         } catch (NoSuchMethodException ex) {
            throw new IllegalArgumentException(
               "ApplicationEvent class [" + applicationEventClass.getName() + "] does not have the required Object constructor: " + ex
            );
         }
      } else {
         throw new IllegalArgumentException("'applicationEventClass' needs to extend ApplicationEvent");
      }
   }

   public void setApplicationEventFactory(EventPublicationInterceptor.ApplicationEventFactory applicationEventFactory) {
      this.applicationEventFactory = applicationEventFactory;
   }

   @Override
   public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
      this.applicationEventPublisher = applicationEventPublisher;
   }

   public void afterPropertiesSet() throws Exception {
      if (this.applicationEventPublisher == null) {
         throw new IllegalArgumentException("Property 'applicationEventPublisher' is required");
      }
   }

   public @Nullable Object invoke(MethodInvocation invocation) throws Throwable {
      Assert.state(this.applicationEventPublisher != null, "No ApplicationEventPublisher available");

      Object retVal;
      try {
         retVal = invocation.proceed();
      } catch (Throwable ex) {
         ApplicationEvent event = this.applicationEventFactory.onFailure(invocation, ex);
         if (event != null) {
            this.applicationEventPublisher.publishEvent(event);
         }

         throw ex;
      }

      ApplicationEvent event = this.applicationEventFactory.onSuccess(invocation, retVal);
      if (event != null) {
         this.applicationEventPublisher.publishEvent(event);
      }

      return retVal;
   }

   @FunctionalInterface
   public interface ApplicationEventFactory {
      @Nullable ApplicationEvent onSuccess(MethodInvocation invocation, @Nullable Object returnValue);

      default @Nullable ApplicationEvent onFailure(MethodInvocation invocation, Throwable failure) {
         return new MethodFailureEvent(invocation, failure);
      }
   }
}
