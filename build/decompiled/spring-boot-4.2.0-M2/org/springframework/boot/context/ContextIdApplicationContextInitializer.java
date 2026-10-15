package org.springframework.boot.context;

import java.util.concurrent.atomic.AtomicLong;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.StringUtils;

public class ContextIdApplicationContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext>, Ordered {
   private int order = 2147483637;

   public void setOrder(int order) {
      this.order = order;
   }

   public int getOrder() {
      return this.order;
   }

   public void initialize(ConfigurableApplicationContext applicationContext) {
      ContextIdApplicationContextInitializer.ContextId contextId = this.getContextId(applicationContext);
      applicationContext.setId(contextId.getId());
      applicationContext.getBeanFactory().registerSingleton(ContextIdApplicationContextInitializer.ContextId.class.getName(), contextId);
   }

   private ContextIdApplicationContextInitializer.ContextId getContextId(ConfigurableApplicationContext applicationContext) {
      ApplicationContext parent = applicationContext.getParent();
      return parent != null && parent.containsBean(ContextIdApplicationContextInitializer.ContextId.class.getName())
         ? ((ContextIdApplicationContextInitializer.ContextId)parent.getBean(ContextIdApplicationContextInitializer.ContextId.class)).createChildId()
         : new ContextIdApplicationContextInitializer.ContextId(this.getApplicationId(applicationContext.getEnvironment()));
   }

   private String getApplicationId(ConfigurableEnvironment environment) {
      String name = environment.getProperty("spring.application.name");
      return StringUtils.hasText(name) ? name : "application";
   }

   static class ContextId {
      private final AtomicLong children = new AtomicLong();
      private final String id;

      ContextId(String id) {
         this.id = id;
      }

      ContextIdApplicationContextInitializer.ContextId createChildId() {
         return new ContextIdApplicationContextInitializer.ContextId(this.id + "-" + this.children.incrementAndGet());
      }

      String getId() {
         return this.id;
      }
   }
}
