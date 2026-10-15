package org.springframework.aop.aspectj.annotation;

import java.io.Serializable;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

public class BeanFactoryAspectInstanceFactory implements MetadataAwareAspectInstanceFactory, Serializable {
   private final BeanFactory beanFactory;
   private final String name;
   private final AspectMetadata aspectMetadata;

   public BeanFactoryAspectInstanceFactory(BeanFactory beanFactory, String name) {
      this(beanFactory, name, null);
   }

   public BeanFactoryAspectInstanceFactory(BeanFactory beanFactory, String name, @Nullable Class<?> type) {
      Assert.notNull(beanFactory, "BeanFactory must not be null");
      Assert.notNull(name, "Bean name must not be null");
      this.beanFactory = beanFactory;
      this.name = name;
      Class<?> resolvedType = type;
      if (resolvedType == null) {
         resolvedType = beanFactory.getType(name);
         Assert.notNull(resolvedType, "Unresolvable bean type - explicitly specify the aspect class");
      }

      this.aspectMetadata = new AspectMetadata(resolvedType, name);
   }

   @Override
   public Object getAspectInstance() {
      return this.beanFactory.getBean(this.name);
   }

   @Override
   public @Nullable ClassLoader getAspectClassLoader() {
      return this.beanFactory instanceof ConfigurableBeanFactory cbf ? cbf.getBeanClassLoader() : ClassUtils.getDefaultClassLoader();
   }

   @Override
   public AspectMetadata getAspectMetadata() {
      return this.aspectMetadata;
   }

   @Override
   public @Nullable Object getAspectCreationMutex() {
      return this.beanFactory.isSingleton(this.name) ? null : this;
   }

   public int getOrder() {
      Class<?> type = this.beanFactory.getType(this.name);
      if (type == null) {
         return Integer.MAX_VALUE;
      }

      if (Ordered.class.isAssignableFrom(type) && this.beanFactory.isSingleton(this.name)) {
         try {
            return ((Ordered)this.beanFactory.getBean(this.name, Ordered.class)).getOrder();
         } catch (BeanNotOfRequiredTypeException var3) {
         }
      }

      return OrderUtils.getOrder(type, Integer.MAX_VALUE);
   }

   @Override
   public String toString() {
      return this.getClass().getSimpleName() + ": bean name '" + this.name + "'";
   }
}
