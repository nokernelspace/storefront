package org.springframework.context.annotation;

import java.util.Arrays;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanDefinitionCustomizer;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.metrics.StartupStep;
import org.springframework.util.Assert;

public class AnnotationConfigApplicationContext extends GenericApplicationContext implements AnnotationConfigRegistry {
   private final AnnotatedBeanDefinitionReader reader = new AnnotatedBeanDefinitionReader(this);
   private final ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(this);

   public AnnotationConfigApplicationContext() {
   }

   public AnnotationConfigApplicationContext(DefaultListableBeanFactory beanFactory) {
      super(beanFactory);
   }

   public AnnotationConfigApplicationContext(Class<?>... componentClasses) {
      this();
      this.register(componentClasses);
      this.refresh();
   }

   public AnnotationConfigApplicationContext(String... basePackages) {
      this();
      this.scan(basePackages);
      this.refresh();
   }

   @Override
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

   @Override
   public void register(Class<?>... componentClasses) {
      Assert.notEmpty(componentClasses, "At least one component class must be specified");
      StartupStep registerComponentClass = this.getApplicationStartup()
         .start("spring.context.component-classes.register")
         .tag("classes", () -> Arrays.toString(componentClasses));
      this.reader.register(componentClasses);
      registerComponentClass.end();
   }

   @Override
   public void scan(String... basePackages) {
      Assert.notEmpty(basePackages, "At least one base package must be specified");
      StartupStep scanPackages = this.getApplicationStartup().start("spring.context.base-packages.scan").tag("packages", () -> Arrays.toString(basePackages));
      this.scanner.scan(basePackages);
      scanPackages.end();
   }

   @Override
   public <T> void registerBean(@Nullable String beanName, Class<T> beanClass, @Nullable Supplier<T> supplier, BeanDefinitionCustomizer... customizers) {
      this.reader.registerBean(beanClass, beanName, supplier, customizers);
   }
}
