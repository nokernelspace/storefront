package org.springframework.context.aot;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotContribution;
import org.springframework.beans.factory.aot.BeanFactoryInitializationAotProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.context.annotation.ReflectiveScan;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.ClassUtils;

class ReflectiveProcessorBeanFactoryInitializationAotProcessor implements BeanFactoryInitializationAotProcessor {
   public @Nullable BeanFactoryInitializationAotContribution processAheadOfTime(ConfigurableListableBeanFactory beanFactory) {
      Class<?>[] beanClasses = Arrays.stream(beanFactory.getBeanDefinitionNames())
         .map(beanName -> RegisteredBean.of(beanFactory, beanName).getBeanClass())
         .toArray(Class[]::new);
      String[] packagesToScan = this.findBasePackagesToScan(beanClasses);
      return new ReflectiveProcessorAotContributionBuilder().withClasses(beanClasses).scan(beanFactory.getBeanClassLoader(), packagesToScan).build();
   }

   protected String[] findBasePackagesToScan(Class<?>[] beanClasses) {
      Set<String> basePackages = new LinkedHashSet<>();

      for (Class<?> beanClass : beanClasses) {
         ReflectiveScan reflectiveScan = (ReflectiveScan)AnnotatedElementUtils.getMergedAnnotation(beanClass, ReflectiveScan.class);
         if (reflectiveScan != null) {
            basePackages.addAll(this.extractBasePackages(reflectiveScan, beanClass));
         }
      }

      return basePackages.toArray(new String[0]);
   }

   private Set<String> extractBasePackages(ReflectiveScan annotation, Class<?> declaringClass) {
      Set<String> basePackages = new LinkedHashSet<>();
      Collections.addAll(basePackages, annotation.basePackages());

      for (Class<?> clazz : annotation.basePackageClasses()) {
         basePackages.add(ClassUtils.getPackageName(clazz));
      }

      if (basePackages.isEmpty()) {
         basePackages.add(ClassUtils.getPackageName(declaringClass));
      }

      return basePackages;
   }
}
