package org.springframework.beans.factory.config;

import java.beans.PropertyEditor;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyEditorRegistrar;
import org.springframework.core.Ordered;

public class CustomEditorConfigurer implements BeanFactoryPostProcessor, Ordered {
   protected final Log logger = LogFactory.getLog(this.getClass());
   private int order = Integer.MAX_VALUE;
   private PropertyEditorRegistrar @Nullable [] propertyEditorRegistrars;
   private @Nullable Map<Class<?>, Class<? extends PropertyEditor>> customEditors;

   public void setOrder(int order) {
      this.order = order;
   }

   public int getOrder() {
      return this.order;
   }

   public void setPropertyEditorRegistrars(PropertyEditorRegistrar[] propertyEditorRegistrars) {
      this.propertyEditorRegistrars = propertyEditorRegistrars;
   }

   public void setCustomEditors(Map<Class<?>, Class<? extends PropertyEditor>> customEditors) {
      this.customEditors = customEditors;
   }

   @Override
   public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
      if (this.propertyEditorRegistrars != null) {
         for (PropertyEditorRegistrar propertyEditorRegistrar : this.propertyEditorRegistrars) {
            beanFactory.addPropertyEditorRegistrar(propertyEditorRegistrar);
         }
      }

      if (this.customEditors != null) {
         this.customEditors.forEach(beanFactory::registerCustomEditor);
      }
   }
}
