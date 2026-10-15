package org.springframework.context.event;

import java.lang.reflect.Method;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;

public class DefaultEventListenerFactory implements EventListenerFactory, Ordered {
   private int order = Integer.MAX_VALUE;

   public void setOrder(int order) {
      this.order = order;
   }

   public int getOrder() {
      return this.order;
   }

   @Override
   public boolean supportsMethod(Method method) {
      return true;
   }

   @Override
   public ApplicationListener<?> createApplicationListener(String beanName, Class<?> type, Method method) {
      return new ApplicationListenerMethodAdapter(beanName, type, method);
   }
}
