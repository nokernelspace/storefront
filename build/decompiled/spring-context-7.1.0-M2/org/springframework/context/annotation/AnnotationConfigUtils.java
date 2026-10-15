package org.springframework.context.annotation;

import java.lang.annotation.Annotation;
import java.util.Set;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.framework.autoproxy.AutoProxyUtils;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.event.DefaultEventListenerFactory;
import org.springframework.context.event.EventListenerMethodProcessor;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

public abstract class AnnotationConfigUtils {
   public static final String CONFIGURATION_ANNOTATION_PROCESSOR_BEAN_NAME = "org.springframework.context.annotation.internalConfigurationAnnotationProcessor";
   public static final String CONFIGURATION_BEAN_NAME_GENERATOR = "org.springframework.context.annotation.internalConfigurationBeanNameGenerator";
   public static final String AUTOWIRED_ANNOTATION_PROCESSOR_BEAN_NAME = "org.springframework.context.annotation.internalAutowiredAnnotationProcessor";
   public static final String COMMON_ANNOTATION_PROCESSOR_BEAN_NAME = "org.springframework.context.annotation.internalCommonAnnotationProcessor";
   public static final String PERSISTENCE_ANNOTATION_PROCESSOR_BEAN_NAME = "org.springframework.context.annotation.internalPersistenceAnnotationProcessor";
   private static final String PERSISTENCE_ANNOTATION_PROCESSOR_CLASS_NAME = "org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor";
   public static final String EVENT_LISTENER_PROCESSOR_BEAN_NAME = "org.springframework.context.event.internalEventListenerProcessor";
   public static final String EVENT_LISTENER_FACTORY_BEAN_NAME = "org.springframework.context.event.internalEventListenerFactory";
   private static final ClassLoader classLoader = AnnotationConfigUtils.class.getClassLoader();
   private static final boolean JAKARTA_ANNOTATIONS_PRESENT = ClassUtils.isPresent("jakarta.annotation.PostConstruct", classLoader);
   private static final boolean JPA_PRESENT = ClassUtils.isPresent("jakarta.persistence.EntityManagerFactory", classLoader)
      && ClassUtils.isPresent("org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor", classLoader);

   public static void registerAnnotationConfigProcessors(BeanDefinitionRegistry registry) {
      registerAnnotationConfigProcessors(registry, null);
   }

   public static Set<BeanDefinitionHolder> registerAnnotationConfigProcessors(BeanDefinitionRegistry registry, @Nullable Object source) {
      DefaultListableBeanFactory beanFactory = unwrapDefaultListableBeanFactory(registry);
      if (beanFactory != null) {
         if (!(beanFactory.getDependencyComparator() instanceof AnnotationAwareOrderComparator)) {
            beanFactory.setDependencyComparator(AnnotationAwareOrderComparator.INSTANCE);
         }

         if (!(beanFactory.getAutowireCandidateResolver() instanceof ContextAnnotationAutowireCandidateResolver)) {
            beanFactory.setAutowireCandidateResolver(new ContextAnnotationAutowireCandidateResolver());
         }
      }

      Set<BeanDefinitionHolder> beanDefs = CollectionUtils.newLinkedHashSet(6);
      if (!registry.containsBeanDefinition("org.springframework.context.annotation.internalConfigurationAnnotationProcessor")) {
         RootBeanDefinition def = new RootBeanDefinition(ConfigurationClassPostProcessor.class);
         def.setSource(source);
         beanDefs.add(registerPostProcessor(registry, def, "org.springframework.context.annotation.internalConfigurationAnnotationProcessor"));
      }

      if (!registry.containsBeanDefinition("org.springframework.context.annotation.internalAutowiredAnnotationProcessor")) {
         RootBeanDefinition def = new RootBeanDefinition(AutowiredAnnotationBeanPostProcessor.class);
         def.setSource(source);
         beanDefs.add(registerPostProcessor(registry, def, "org.springframework.context.annotation.internalAutowiredAnnotationProcessor"));
      }

      if (JAKARTA_ANNOTATIONS_PRESENT && !registry.containsBeanDefinition("org.springframework.context.annotation.internalCommonAnnotationProcessor")) {
         RootBeanDefinition def = new RootBeanDefinition(CommonAnnotationBeanPostProcessor.class);
         def.setSource(source);
         beanDefs.add(registerPostProcessor(registry, def, "org.springframework.context.annotation.internalCommonAnnotationProcessor"));
      }

      if (JPA_PRESENT && !registry.containsBeanDefinition("org.springframework.context.annotation.internalPersistenceAnnotationProcessor")) {
         RootBeanDefinition def = new RootBeanDefinition();

         try {
            def.setBeanClass(
               ClassUtils.forName("org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor", AnnotationConfigUtils.class.getClassLoader())
            );
         } catch (ClassNotFoundException ex) {
            throw new IllegalStateException(
               "Cannot load optional framework class: org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor", ex
            );
         }

         def.setSource(source);
         beanDefs.add(registerPostProcessor(registry, def, "org.springframework.context.annotation.internalPersistenceAnnotationProcessor"));
      }

      if (!registry.containsBeanDefinition("org.springframework.context.event.internalEventListenerProcessor")) {
         RootBeanDefinition def = new RootBeanDefinition(EventListenerMethodProcessor.class);
         def.setSource(source);
         beanDefs.add(registerPostProcessor(registry, def, "org.springframework.context.event.internalEventListenerProcessor"));
      }

      if (!registry.containsBeanDefinition("org.springframework.context.event.internalEventListenerFactory")) {
         RootBeanDefinition def = new RootBeanDefinition(DefaultEventListenerFactory.class);
         def.setSource(source);
         beanDefs.add(registerPostProcessor(registry, def, "org.springframework.context.event.internalEventListenerFactory"));
      }

      return beanDefs;
   }

   private static BeanDefinitionHolder registerPostProcessor(BeanDefinitionRegistry registry, RootBeanDefinition definition, String beanName) {
      definition.setRole(2);
      registry.registerBeanDefinition(beanName, definition);
      return new BeanDefinitionHolder(definition, beanName);
   }

   private static @Nullable DefaultListableBeanFactory unwrapDefaultListableBeanFactory(BeanDefinitionRegistry registry) {
      if (registry instanceof DefaultListableBeanFactory dlbf) {
         return dlbf;
      } else {
         return registry instanceof GenericApplicationContext gac ? gac.getDefaultListableBeanFactory() : null;
      }
   }

   public static void processCommonDefinitionAnnotations(AnnotatedBeanDefinition abd) {
      processCommonDefinitionAnnotations(abd, abd.getMetadata());
   }

   static void processCommonDefinitionAnnotations(AnnotatedBeanDefinition abd, AnnotatedTypeMetadata metadata) {
      AnnotationAttributes lazy = attributesFor(metadata, Lazy.class);
      if (lazy != null) {
         abd.setLazyInit(lazy.getBoolean("value"));
      } else if (abd.getMetadata() != metadata) {
         lazy = attributesFor(abd.getMetadata(), Lazy.class);
         if (lazy != null) {
            abd.setLazyInit(lazy.getBoolean("value"));
         }
      }

      if (metadata.isAnnotated(Primary.class.getName())) {
         abd.setPrimary(true);
      }

      if (metadata.isAnnotated(Fallback.class.getName())) {
         abd.setFallback(true);
      }

      AnnotationAttributes dependsOn = attributesFor(metadata, DependsOn.class);
      if (dependsOn != null) {
         abd.setDependsOn(dependsOn.getStringArray("value"));
      }

      AnnotationAttributes role = attributesFor(metadata, Role.class);
      if (role != null) {
         abd.setRole(role.getNumber("value").intValue());
      }

      AnnotationAttributes description = attributesFor(metadata, Description.class);
      if (description != null) {
         abd.setDescription(description.getString("value"));
      }

      AnnotationAttributes proxyable = attributesFor(metadata, Proxyable.class);
      if (proxyable != null) {
         ProxyType mode = (ProxyType)proxyable.getEnum("value");
         if (mode == ProxyType.TARGET_CLASS) {
            abd.setAttribute(AutoProxyUtils.PRESERVE_TARGET_CLASS_ATTRIBUTE, Boolean.TRUE);
         } else {
            Class<?>[] ifcs = proxyable.getClassArray("interfaces");
            if (ifcs.length > 0 || mode == ProxyType.INTERFACES) {
               abd.setAttribute(AutoProxyUtils.EXPOSED_INTERFACES_ATTRIBUTE, ifcs);
            }
         }
      }
   }

   static BeanDefinitionHolder applyScopedProxyMode(ScopeMetadata metadata, BeanDefinitionHolder definition, BeanDefinitionRegistry registry) {
      ScopedProxyMode scopedProxyMode = metadata.getScopedProxyMode();
      if (scopedProxyMode.equals(ScopedProxyMode.NO)) {
         return definition;
      }

      boolean proxyTargetClass = scopedProxyMode.equals(ScopedProxyMode.TARGET_CLASS);
      return ScopedProxyCreator.createScopedProxy(definition, registry, proxyTargetClass);
   }

   static @Nullable AnnotationAttributes attributesFor(AnnotatedTypeMetadata metadata, Class<?> annotationType) {
      return attributesFor(metadata, annotationType.getName());
   }

   static @Nullable AnnotationAttributes attributesFor(AnnotatedTypeMetadata metadata, String annotationTypeName) {
      return AnnotationAttributes.fromMap(metadata.getAnnotationAttributes(annotationTypeName));
   }

   static Set<AnnotationAttributes> attributesForRepeatable(
      AnnotationMetadata metadata,
      Class<? extends Annotation> annotationType,
      Class<? extends Annotation> containerType,
      Predicate<MergedAnnotation<? extends Annotation>> predicate
   ) {
      return metadata.getMergedRepeatableAnnotationAttributes(annotationType, containerType, predicate, false, false);
   }

   static Set<AnnotationAttributes> attributesForRepeatable(
      AnnotationMetadata metadata, Class<? extends Annotation> annotationType, Class<? extends Annotation> containerType, boolean sortByReversedMetaDistance
   ) {
      return metadata.getMergedRepeatableAnnotationAttributes(annotationType, containerType, false, sortByReversedMetaDistance);
   }
}
