package org.springframework.boot.builder;

import java.lang.ref.WeakReference;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.core.Ordered;
import org.springframework.util.ObjectUtils;

public class ParentContextCloserApplicationListener
   implements ApplicationListener<ParentContextApplicationContextInitializer.ParentContextAvailableEvent>,
   ApplicationContextAware,
   Ordered {
   private static final int ORDER = 2147483637;
   private ApplicationContext context;

   public int getOrder() {
      return 2147483637;
   }

   public void setApplicationContext(ApplicationContext context) throws BeansException {
      this.context = context;
   }

   public void onApplicationEvent(ParentContextApplicationContextInitializer.ParentContextAvailableEvent event) {
      this.maybeInstallListenerInParent(event.getApplicationContext());
   }

   private void maybeInstallListenerInParent(ConfigurableApplicationContext child) {
      if (child == this.context && child.getParent() instanceof ConfigurableApplicationContext parent) {
         parent.addApplicationListener(this.createContextCloserListener(child));
      }
   }

   protected ParentContextCloserApplicationListener.ContextCloserListener createContextCloserListener(ConfigurableApplicationContext child) {
      return new ParentContextCloserApplicationListener.ContextCloserListener(child);
   }

   protected static class ContextCloserListener implements ApplicationListener<ContextClosedEvent> {
      private final WeakReference<ConfigurableApplicationContext> childContext;

      public ContextCloserListener(ConfigurableApplicationContext childContext) {
         this.childContext = new WeakReference<>(childContext);
      }

      public void onApplicationEvent(ContextClosedEvent event) {
         ConfigurableApplicationContext context = this.childContext.get();
         if (context != null && event.getApplicationContext() == context.getParent() && context.isActive()) {
            context.close();
         }
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         } else if (obj == null) {
            return false;
         } else {
            return obj instanceof ParentContextCloserApplicationListener.ContextCloserListener other
               ? ObjectUtils.nullSafeEquals(this.childContext.get(), other.childContext.get())
               : super.equals(obj);
         }
      }

      @Override
      public int hashCode() {
         return ObjectUtils.nullSafeHashCode(this.childContext.get());
      }
   }
}
