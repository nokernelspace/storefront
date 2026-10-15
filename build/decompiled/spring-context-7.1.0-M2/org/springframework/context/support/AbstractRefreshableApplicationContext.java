package org.springframework.context.support;

import java.io.IOException;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextException;

public abstract class AbstractRefreshableApplicationContext extends AbstractApplicationContext {
   private @Nullable Boolean allowBeanDefinitionOverriding;
   private @Nullable Boolean allowCircularReferences;
   private volatile @Nullable DefaultListableBeanFactory beanFactory;

   public AbstractRefreshableApplicationContext() {
   }

   public AbstractRefreshableApplicationContext(@Nullable ApplicationContext parent) {
      super(parent);
   }

   public void setAllowBeanDefinitionOverriding(boolean allowBeanDefinitionOverriding) {
      this.allowBeanDefinitionOverriding = allowBeanDefinitionOverriding;
   }

   public void setAllowCircularReferences(boolean allowCircularReferences) {
      this.allowCircularReferences = allowCircularReferences;
   }

   @Override
   protected final void refreshBeanFactory() throws BeansException {
      if (this.hasBeanFactory()) {
         this.destroyBeans();
         this.closeBeanFactory();
      }

      try {
         DefaultListableBeanFactory beanFactory = this.createBeanFactory();
         beanFactory.setSerializationId(this.getId());
         beanFactory.setApplicationStartup(this.getApplicationStartup());
         this.customizeBeanFactory(beanFactory);
         this.loadBeanDefinitions(beanFactory);
         this.beanFactory = beanFactory;
      } catch (IOException ex) {
         throw new ApplicationContextException("I/O error parsing bean definition source for " + this.getDisplayName(), ex);
      }
   }

   @Override
   protected void cancelRefresh(Throwable ex) {
      DefaultListableBeanFactory beanFactory = this.beanFactory;
      if (beanFactory != null) {
         beanFactory.setSerializationId(null);
      }

      super.cancelRefresh(ex);
   }

   @Override
   protected final void closeBeanFactory() {
      DefaultListableBeanFactory beanFactory = this.beanFactory;
      if (beanFactory != null) {
         beanFactory.setSerializationId(null);
         this.beanFactory = null;
      }
   }

   protected final boolean hasBeanFactory() {
      return this.beanFactory != null;
   }

   @Override
   public final ConfigurableListableBeanFactory getBeanFactory() {
      DefaultListableBeanFactory beanFactory = this.beanFactory;
      if (beanFactory == null) {
         throw new IllegalStateException("BeanFactory not initialized or already closed - call 'refresh' before accessing beans via the ApplicationContext");
      } else {
         return beanFactory;
      }
   }

   @Override
   protected void assertBeanFactoryActive() {
   }

   protected DefaultListableBeanFactory createBeanFactory() {
      return new DefaultListableBeanFactory(this.getInternalParentBeanFactory());
   }

   protected void customizeBeanFactory(DefaultListableBeanFactory beanFactory) {
      if (this.allowBeanDefinitionOverriding != null) {
         beanFactory.setAllowBeanDefinitionOverriding(this.allowBeanDefinitionOverriding);
      }

      if (this.allowCircularReferences != null) {
         beanFactory.setAllowCircularReferences(this.allowCircularReferences);
      }
   }

   protected abstract void loadBeanDefinitions(DefaultListableBeanFactory beanFactory) throws BeansException, IOException;
}
