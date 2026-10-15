package org.springframework.context.annotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.MultiValueMap;

class ConditionEvaluator {
   private final ConditionEvaluator.ConditionContextImpl context;

   public ConditionEvaluator(@Nullable BeanDefinitionRegistry registry, @Nullable Environment environment, @Nullable ResourceLoader resourceLoader) {
      this.context = new ConditionEvaluator.ConditionContextImpl(registry, environment, resourceLoader);
   }

   public boolean shouldSkip(AnnotatedTypeMetadata metadata) {
      return this.shouldSkip(metadata, null);
   }

   public boolean shouldSkip(@Nullable AnnotatedTypeMetadata metadata, ConfigurationCondition.@Nullable ConfigurationPhase phase) {
      if (metadata != null && metadata.isAnnotated(Conditional.class.getName())) {
         if (phase == null) {
            return metadata instanceof AnnotationMetadata annotationMetadata && ConfigurationClassUtils.isConfigurationCandidate(annotationMetadata)
               ? this.shouldSkip(metadata, ConfigurationCondition.ConfigurationPhase.PARSE_CONFIGURATION)
               : this.shouldSkip(metadata, ConfigurationCondition.ConfigurationPhase.REGISTER_BEAN);
         }

         for (Condition condition : this.collectConditions(metadata)) {
            ConfigurationCondition.ConfigurationPhase requiredPhase = null;
            if (condition instanceof ConfigurationCondition configurationCondition) {
               requiredPhase = configurationCondition.getConfigurationPhase();
            }

            if ((requiredPhase == null || requiredPhase == phase) && !condition.matches(this.context, metadata)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   List<Condition> collectConditions(@Nullable AnnotatedTypeMetadata metadata) {
      if (metadata != null && metadata.isAnnotated(Conditional.class.getName())) {
         List<Condition> conditions = new ArrayList<>();

         for (String[] conditionClasses : this.getConditionClasses(metadata)) {
            for (String conditionClass : conditionClasses) {
               Condition condition = this.getCondition(conditionClass, this.context.getClassLoader());
               conditions.add(condition);
            }
         }

         AnnotationAwareOrderComparator.sort(conditions);
         return conditions;
      } else {
         return Collections.emptyList();
      }
   }

   private List<String[]> getConditionClasses(AnnotatedTypeMetadata metadata) {
      MultiValueMap<String, Object> attributes = metadata.getAllAnnotationAttributes(Conditional.class.getName(), true);
      Object values = attributes != null ? attributes.get("value") : null;
      return (List<String[]>)(values != null ? values : Collections.emptyList());
   }

   private Condition getCondition(String conditionClassName, @Nullable ClassLoader classloader) {
      Class<?> conditionClass = ClassUtils.resolveClassName(conditionClassName, classloader);
      return (Condition)BeanUtils.instantiateClass(conditionClass);
   }

   private static class ConditionContextImpl implements ConditionContext {
      private final @Nullable BeanDefinitionRegistry registry;
      private final @Nullable ConfigurableListableBeanFactory beanFactory;
      private final Environment environment;
      private final ResourceLoader resourceLoader;
      private final @Nullable ClassLoader classLoader;

      public ConditionContextImpl(@Nullable BeanDefinitionRegistry registry, @Nullable Environment environment, @Nullable ResourceLoader resourceLoader) {
         this.registry = registry;
         this.beanFactory = deduceBeanFactory(registry);
         this.environment = environment != null ? environment : deduceEnvironment(registry);
         this.resourceLoader = resourceLoader != null ? resourceLoader : deduceResourceLoader(registry);
         this.classLoader = deduceClassLoader(resourceLoader, this.beanFactory);
      }

      private static @Nullable ConfigurableListableBeanFactory deduceBeanFactory(@Nullable BeanDefinitionRegistry source) {
         if (source instanceof ConfigurableListableBeanFactory configurableListableBeanFactory) {
            return configurableListableBeanFactory;
         } else {
            return source instanceof ConfigurableApplicationContext configurableApplicationContext ? configurableApplicationContext.getBeanFactory() : null;
         }
      }

      private static Environment deduceEnvironment(@Nullable BeanDefinitionRegistry source) {
         return (Environment)(source instanceof EnvironmentCapable environmentCapable ? environmentCapable.getEnvironment() : new StandardEnvironment());
      }

      private static ResourceLoader deduceResourceLoader(@Nullable BeanDefinitionRegistry source) {
         return (ResourceLoader)(source instanceof ResourceLoader resourceLoader ? resourceLoader : new DefaultResourceLoader());
      }

      private static @Nullable ClassLoader deduceClassLoader(@Nullable ResourceLoader resourceLoader, @Nullable ConfigurableListableBeanFactory beanFactory) {
         if (resourceLoader != null) {
            ClassLoader classLoader = resourceLoader.getClassLoader();
            if (classLoader != null) {
               return classLoader;
            }
         }

         return beanFactory != null ? beanFactory.getBeanClassLoader() : ClassUtils.getDefaultClassLoader();
      }

      @Override
      public BeanDefinitionRegistry getRegistry() {
         Assert.state(this.registry != null, "No BeanDefinitionRegistry available");
         return this.registry;
      }

      @Override
      public @Nullable ConfigurableListableBeanFactory getBeanFactory() {
         return this.beanFactory;
      }

      @Override
      public Environment getEnvironment() {
         return this.environment;
      }

      @Override
      public ResourceLoader getResourceLoader() {
         return this.resourceLoader;
      }

      @Override
      public @Nullable ClassLoader getClassLoader() {
         return this.classLoader;
      }
   }
}
