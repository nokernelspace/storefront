package org.springframework.beans.factory.aot;

import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.core.log.LogMessage;

abstract class AutowiredElementResolver {
   private final Log logger = LogFactory.getLog(this.getClass());

   protected final void registerDependentBeans(ConfigurableBeanFactory beanFactory, String beanName, Set<String> autowiredBeanNames) {
      for (String autowiredBeanName : autowiredBeanNames) {
         if (beanFactory.containsBean(autowiredBeanName)) {
            beanFactory.registerDependentBean(autowiredBeanName, beanName);
         }

         this.logger.trace(LogMessage.format("Autowiring by type from bean name %s' to bean named '%s'", beanName, autowiredBeanName));
      }
   }

   static class ShortcutDependencyDescriptor extends DependencyDescriptor {
      private final String shortcut;

      public ShortcutDependencyDescriptor(DependencyDescriptor original, String shortcut) {
         super(original);
         this.shortcut = shortcut;
      }

      @Override
      public Object resolveShortcut(BeanFactory beanFactory) {
         return beanFactory.getBean(this.shortcut, this.getDependencyType());
      }
   }
}
