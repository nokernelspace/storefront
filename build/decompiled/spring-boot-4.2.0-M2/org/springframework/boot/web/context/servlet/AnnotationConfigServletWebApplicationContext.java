package org.springframework.boot.web.context.servlet;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanDefinitionCustomizer;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.AnnotationConfigRegistry;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ScopeMetadataResolver;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.web.context.support.GenericWebApplicationContext;

public class AnnotationConfigServletWebApplicationContext extends GenericWebApplicationContext implements AnnotationConfigRegistry {
   private final AnnotatedBeanDefinitionReader reader;
   private final ClassPathBeanDefinitionScanner scanner;
   private final Set<Class<?>> annotatedClasses = new LinkedHashSet<>();
   private String @Nullable [] basePackages;

   public AnnotationConfigServletWebApplicationContext() {
      this.reader = new AnnotatedBeanDefinitionReader(this);
      this.scanner = new ClassPathBeanDefinitionScanner(this);
   }

   public AnnotationConfigServletWebApplicationContext(DefaultListableBeanFactory beanFactory) {
      super(beanFactory);
      this.reader = new AnnotatedBeanDefinitionReader(this);
      this.scanner = new ClassPathBeanDefinitionScanner(this);
   }

   public AnnotationConfigServletWebApplicationContext(Class<?>... annotatedClasses) {
      this();
      this.register(annotatedClasses);
      this.refresh();
   }

   public AnnotationConfigServletWebApplicationContext(String... basePackages) {
      this();
      this.scan(basePackages);
      this.refresh();
   }

   public void setEnvironment(ConfigurableEnvironment environment) {
      super.setEnvironment(environment);
      this.reader.setEnvironment(environment);
      this.scanner.setEnvironment(environment);
   }

   public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
      this.reader.setBeanNameGenerator(beanNameGenerator);
      this.scanner.setBeanNameGenerator(beanNameGenerator);
      this.getBeanFactory().registerSingleton("org.springframework.context.annotation.internalConfigurationBeanNameGenerator", beanNameGenerator);
   }

   public void setScopeMetadataResolver(ScopeMetadataResolver scopeMetadataResolver) {
      this.reader.setScopeMetadataResolver(scopeMetadataResolver);
      this.scanner.setScopeMetadataResolver(scopeMetadataResolver);
   }

   public final void register(Class<?>... annotatedClasses) {
      Assert.notEmpty(annotatedClasses, "'annotatedClasses' must not be empty");
      this.annotatedClasses.addAll(Arrays.asList(annotatedClasses));
   }

   public final void scan(String... basePackages) {
      Assert.notEmpty(basePackages, "'basePackages' must not be empty");
      this.basePackages = basePackages;
   }

   protected void prepareRefresh() {
      this.scanner.clearCache();
      super.prepareRefresh();
   }

   protected void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
      super.postProcessBeanFactory(beanFactory);
      if (!ObjectUtils.isEmpty(this.basePackages)) {
         this.scanner.scan(this.basePackages);
      }

      if (!this.annotatedClasses.isEmpty()) {
         this.reader.register(ClassUtils.toClassArray(this.annotatedClasses));
      }
   }

   public <T> void registerBean(@Nullable String beanName, Class<T> beanClass, @Nullable Supplier<T> supplier, BeanDefinitionCustomizer... customizers) {
      this.reader.registerBean(beanClass, beanName, supplier, customizers);
   }
}
