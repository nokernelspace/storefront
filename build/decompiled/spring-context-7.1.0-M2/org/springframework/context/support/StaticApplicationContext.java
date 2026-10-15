package org.springframework.context.support;

import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.context.ApplicationContext;

public class StaticApplicationContext extends GenericApplicationContext {
   private final StaticMessageSource staticMessageSource = new StaticMessageSource();

   public StaticApplicationContext() throws BeansException {
      this(null);
   }

   public StaticApplicationContext(@Nullable ApplicationContext parent) throws BeansException {
      super(parent);
      this.getBeanFactory().registerSingleton("messageSource", this.staticMessageSource);
   }

   @Override
   protected void assertBeanFactoryActive() {
   }

   public final StaticMessageSource getStaticMessageSource() {
      return this.staticMessageSource;
   }

   public void registerSingleton(String name, Class<?> clazz) throws BeansException {
      GenericBeanDefinition bd = new GenericBeanDefinition();
      bd.setBeanClass(clazz);
      this.getDefaultListableBeanFactory().registerBeanDefinition(name, bd);
   }

   public void registerSingleton(String name, Class<?> clazz, MutablePropertyValues pvs) throws BeansException {
      GenericBeanDefinition bd = new GenericBeanDefinition();
      bd.setBeanClass(clazz);
      bd.setPropertyValues(pvs);
      this.getDefaultListableBeanFactory().registerBeanDefinition(name, bd);
   }

   public void registerPrototype(String name, Class<?> clazz) throws BeansException {
      GenericBeanDefinition bd = new GenericBeanDefinition();
      bd.setScope("prototype");
      bd.setBeanClass(clazz);
      this.getDefaultListableBeanFactory().registerBeanDefinition(name, bd);
   }

   public void registerPrototype(String name, Class<?> clazz, MutablePropertyValues pvs) throws BeansException {
      GenericBeanDefinition bd = new GenericBeanDefinition();
      bd.setScope("prototype");
      bd.setBeanClass(clazz);
      bd.setPropertyValues(pvs);
      this.getDefaultListableBeanFactory().registerBeanDefinition(name, bd);
   }

   public void addMessage(String code, Locale locale, String defaultMessage) {
      this.getStaticMessageSource().addMessage(code, locale, defaultMessage);
   }
}
