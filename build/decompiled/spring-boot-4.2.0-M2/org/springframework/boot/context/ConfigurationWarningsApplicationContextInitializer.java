package org.springframework.boot.context;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.core.PriorityOrdered;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

public class ConfigurationWarningsApplicationContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
   private static final Log logger = LogFactory.getLog(ConfigurationWarningsApplicationContextInitializer.class);

   public void initialize(ConfigurableApplicationContext context) {
      context.addBeanFactoryPostProcessor(new ConfigurationWarningsApplicationContextInitializer.ConfigurationWarningsPostProcessor(this.getChecks()));
   }

   protected ConfigurationWarningsApplicationContextInitializer.Check[] getChecks() {
      return new ConfigurationWarningsApplicationContextInitializer.Check[]{new ConfigurationWarningsApplicationContextInitializer.ComponentScanPackageCheck()};
   }

   @FunctionalInterface
   protected interface Check {
      @Nullable String getWarning(BeanDefinitionRegistry registry);
   }

   protected static class ComponentScanPackageCheck implements ConfigurationWarningsApplicationContextInitializer.Check {
      private static final Set<String> PROBLEM_PACKAGES;

      @Override
      public @Nullable String getWarning(BeanDefinitionRegistry registry) {
         Set<String> scannedPackages = this.getComponentScanningPackages(registry);
         List<String> problematicPackages = this.getProblematicPackages(scannedPackages);
         return problematicPackages.isEmpty()
            ? null
            : "Your ApplicationContext is unlikely to start due to a @ComponentScan of "
               + StringUtils.collectionToDelimitedString(problematicPackages, ", ")
               + ".";
      }

      protected Set<String> getComponentScanningPackages(BeanDefinitionRegistry registry) {
         Set<String> packages = new LinkedHashSet<>();
         String[] names = registry.getBeanDefinitionNames();

         for (String name : names) {
            if (registry.getBeanDefinition(name) instanceof AnnotatedBeanDefinition annotatedDefinition) {
               this.addComponentScanningPackages(packages, annotatedDefinition.getMetadata());
            }
         }

         return packages;
      }

      private void addComponentScanningPackages(Set<String> packages, AnnotationMetadata metadata) {
         AnnotationAttributes attributes = AnnotationAttributes.fromMap(metadata.getAnnotationAttributes(ComponentScan.class.getName(), true));
         if (attributes != null) {
            this.addPackages(packages, attributes.getStringArray("value"));
            this.addPackages(packages, attributes.getStringArray("basePackages"));
            this.addClasses(packages, attributes.getStringArray("basePackageClasses"));
            if (packages.isEmpty()) {
               packages.add(ClassUtils.getPackageName(metadata.getClassName()));
            }
         }
      }

      private void addPackages(Set<String> packages, String @Nullable [] values) {
         if (values != null) {
            Collections.addAll(packages, values);
         }
      }

      private void addClasses(Set<String> packages, String @Nullable [] values) {
         if (values != null) {
            for (String value : values) {
               packages.add(ClassUtils.getPackageName(value));
            }
         }
      }

      private List<String> getProblematicPackages(Set<String> scannedPackages) {
         List<String> problematicPackages = new ArrayList<>();

         for (String scannedPackage : scannedPackages) {
            if (this.isProblematicPackage(scannedPackage)) {
               problematicPackages.add(this.getDisplayName(scannedPackage));
            }
         }

         return problematicPackages;
      }

      private boolean isProblematicPackage(@Nullable String scannedPackage) {
         return scannedPackage != null && !scannedPackage.isEmpty() ? PROBLEM_PACKAGES.contains(scannedPackage) : true;
      }

      private String getDisplayName(@Nullable String scannedPackage) {
         return scannedPackage != null && !scannedPackage.isEmpty() ? "'" + scannedPackage + "'" : "the default package";
      }

      static {
         Set<String> packages = new HashSet<>();
         packages.add("org.springframework");
         packages.add("org");
         PROBLEM_PACKAGES = Collections.unmodifiableSet(packages);
      }
   }

   protected static final class ConfigurationWarningsPostProcessor implements PriorityOrdered, BeanDefinitionRegistryPostProcessor {
      private final ConfigurationWarningsApplicationContextInitializer.Check[] checks;

      public ConfigurationWarningsPostProcessor(ConfigurationWarningsApplicationContextInitializer.Check[] checks) {
         this.checks = checks;
      }

      public int getOrder() {
         return 2147483646;
      }

      public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
      }

      public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
         for (ConfigurationWarningsApplicationContextInitializer.Check check : this.checks) {
            String message = check.getWarning(registry);
            if (StringUtils.hasLength(message)) {
               this.warn(message);
            }
         }
      }

      private void warn(String message) {
         if (ConfigurationWarningsApplicationContextInitializer.logger.isWarnEnabled()) {
            ConfigurationWarningsApplicationContextInitializer.logger.warn(String.format("%n%n** WARNING ** : %s%n%n", message));
         }
      }
   }
}
