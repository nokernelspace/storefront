package org.springframework.boot.builder;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.core.Ordered;

public class ParentContextApplicationContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {
   private int order = Integer.MIN_VALUE;
   private final ApplicationContext parent;

   public ParentContextApplicationContextInitializer(ApplicationContext parent) {
      this.parent = parent;
   }

   public void setOrder(int order) {
      this.order = order;
   }

   public int getOrder() {
      return this.order;
   }

   public void initialize(ConfigurableApplicationContext applicationContext) {
      if (applicationContext != this.parent) {
         applicationContext.setParent(this.parent);
         applicationContext.addApplicationListener(ParentContextApplicationContextInitializer.EventPublisher.INSTANCE);
      }
   }

   private static final class EventPublisher implements ApplicationListener<ContextRefreshedEvent>, Ordered {
      private static final ParentContextApplicationContextInitializer.EventPublisher INSTANCE = new ParentContextApplicationContextInitializer.EventPublisher();

      public int getOrder() {
         return Integer.MIN_VALUE;
      }

      public void onApplicationEvent(ContextRefreshedEvent event) {
         ApplicationContext context = event.getApplicationContext();
         if (context instanceof ConfigurableApplicationContext configurableApplicationContext && context == event.getSource()) {
            context.publishEvent(new ParentContextApplicationContextInitializer.ParentContextAvailableEvent(configurableApplicationContext));
         }
      }
   }

   public static class ParentContextAvailableEvent extends ApplicationEvent {
      public ParentContextAvailableEvent(ConfigurableApplicationContext applicationContext) {
         super(applicationContext);
      }

      public ConfigurableApplicationContext getApplicationContext() {
         return (ConfigurableApplicationContext)this.getSource();
      }
   }
}
