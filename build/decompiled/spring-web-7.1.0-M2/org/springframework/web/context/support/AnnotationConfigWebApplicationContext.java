package org.springframework.web.context.support;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanRegistrar;
import org.springframework.beans.factory.support.BeanNameGenerator;
import org.springframework.beans.factory.support.BeanRegistryAdapter;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotatedBeanDefinitionReader;
import org.springframework.context.annotation.AnnotationConfigRegistry;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.ScopeMetadataResolver;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

public class AnnotationConfigWebApplicationContext extends AbstractRefreshableWebApplicationContext implements AnnotationConfigRegistry {
   private @Nullable BeanNameGenerator beanNameGenerator;
   private @Nullable ScopeMetadataResolver scopeMetadataResolver;
   private final Set<BeanRegistrar> beanRegistrars = new LinkedHashSet<>();
   private final Set<Class<?>> componentClasses = new LinkedHashSet<>();
   private final Set<String> basePackages = new LinkedHashSet<>();

   public void setBeanNameGenerator(@Nullable BeanNameGenerator beanNameGenerator) {
      this.beanNameGenerator = beanNameGenerator;
   }

   protected @Nullable BeanNameGenerator getBeanNameGenerator() {
      return this.beanNameGenerator;
   }

   public void setScopeMetadataResolver(@Nullable ScopeMetadataResolver scopeMetadataResolver) {
      this.scopeMetadataResolver = scopeMetadataResolver;
   }

   protected @Nullable ScopeMetadataResolver getScopeMetadataResolver() {
      return this.scopeMetadataResolver;
   }

   public void register(BeanRegistrar... registrars) {
      Assert.notEmpty(registrars, "At least one BeanRegistrar must be specified");
      Collections.addAll(this.beanRegistrars, registrars);
   }

   public void register(Class<?>... componentClasses) {
      Assert.notEmpty(componentClasses, "At least one component class must be specified");
      Collections.addAll(this.componentClasses, componentClasses);
   }

   public void scan(String... basePackages) {
      Assert.notEmpty(basePackages, "At least one base package must be specified");
      Collections.addAll(this.basePackages, basePackages);
   }

   protected void loadBeanDefinitions(DefaultListableBeanFactory beanFactory) {
      AnnotatedBeanDefinitionReader reader = this.getAnnotatedBeanDefinitionReader(beanFactory);
      ClassPathBeanDefinitionScanner scanner = this.getClassPathBeanDefinitionScanner(beanFactory);
      BeanNameGenerator beanNameGenerator = this.getBeanNameGenerator();
      if (beanNameGenerator != null) {
         reader.setBeanNameGenerator(beanNameGenerator);
         scanner.setBeanNameGenerator(beanNameGenerator);
         beanFactory.registerSingleton("org.springframework.context.annotation.internalConfigurationBeanNameGenerator", beanNameGenerator);
      }

      ScopeMetadataResolver scopeMetadataResolver = this.getScopeMetadataResolver();
      if (scopeMetadataResolver != null) {
         reader.setScopeMetadataResolver(scopeMetadataResolver);
         scanner.setScopeMetadataResolver(scopeMetadataResolver);
      }

      if (!this.beanRegistrars.isEmpty()) {
         if (this.logger.isDebugEnabled()) {
            this.logger.debug("Applying bean registrars: [" + StringUtils.collectionToCommaDelimitedString(this.beanRegistrars) + "]");
         }

         for (BeanRegistrar registrar : this.beanRegistrars) {
            new BeanRegistryAdapter(beanFactory, this.getEnvironment(), registrar.getClass()).register(registrar);
         }
      }

      if (!this.componentClasses.isEmpty()) {
         if (this.logger.isDebugEnabled()) {
            this.logger.debug("Registering component classes: [" + StringUtils.collectionToCommaDelimitedString(this.componentClasses) + "]");
         }

         reader.register(ClassUtils.toClassArray(this.componentClasses));
      }

      if (!this.basePackages.isEmpty()) {
         if (this.logger.isDebugEnabled()) {
            this.logger.debug("Scanning base packages: [" + StringUtils.collectionToCommaDelimitedString(this.basePackages) + "]");
         }

         scanner.scan(StringUtils.toStringArray(this.basePackages));
      }

      String[] configLocations = this.getConfigLocations();
      if (configLocations != null) {
         for (String configLocation : configLocations) {
            try {
               Class<?> clazz = ClassUtils.forName(configLocation, this.getClassLoader());
               if (this.logger.isTraceEnabled()) {
                  this.logger.trace("Registering [" + configLocation + "]");
               }

               reader.register(new Class[]{clazz});
            } catch (ClassNotFoundException ex) {
               if (this.logger.isTraceEnabled()) {
                  this.logger.trace("Could not load class for config location [" + configLocation + "] - trying package scan. " + ex);
               }

               int count = scanner.scan(new String[]{configLocation});
               if (count == 0 && this.logger.isDebugEnabled()) {
                  this.logger.debug("No component classes found for specified class/package [" + configLocation + "]");
               }
            }
         }
      }
   }

   protected AnnotatedBeanDefinitionReader getAnnotatedBeanDefinitionReader(DefaultListableBeanFactory beanFactory) {
      return new AnnotatedBeanDefinitionReader(beanFactory, this.getEnvironment());
   }

   protected ClassPathBeanDefinitionScanner getClassPathBeanDefinitionScanner(DefaultListableBeanFactory beanFactory) {
      return new ClassPathBeanDefinitionScanner(beanFactory, true, this.getEnvironment());
   }
}
