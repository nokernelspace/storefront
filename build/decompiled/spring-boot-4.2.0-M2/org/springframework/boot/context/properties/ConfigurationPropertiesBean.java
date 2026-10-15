package org.springframework.boot.context.properties;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.context.properties.bind.BindConstructorProvider;
import org.springframework.boot.context.properties.bind.BindMethod;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;
import org.springframework.lang.Contract;
import org.springframework.util.Assert;
import org.springframework.validation.annotation.Validated;

public final class ConfigurationPropertiesBean {
   private final String name;
   private final @Nullable Object instance;
   private final Bindable<?> bindTarget;

   private ConfigurationPropertiesBean(String name, @Nullable Object instance, Bindable<?> bindTarget) {
      this.name = name;
      this.instance = instance;
      this.bindTarget = bindTarget;
   }

   public String getName() {
      return this.name;
   }

   public @Nullable Object getInstance() {
      return this.instance;
   }

   Class<?> getType() {
      Class<?> resolved = this.bindTarget.getType().resolve();
      Assert.state(resolved != null, "'resolved' must not be null");
      return resolved;
   }

   public ConfigurationProperties getAnnotation() {
      ConfigurationProperties annotation = this.bindTarget.getAnnotation(ConfigurationProperties.class);
      Assert.state(annotation != null, "'annotation' must not be null");
      return annotation;
   }

   public Bindable<?> asBindTarget() {
      return this.bindTarget;
   }

   public static Map<String, ConfigurationPropertiesBean> getAll(ApplicationContext applicationContext) {
      Assert.notNull(applicationContext, "'applicationContext' must not be null");
      if (applicationContext instanceof ConfigurableApplicationContext configurableContext) {
         return getAll(configurableContext);
      } else {
         Map<String, ConfigurationPropertiesBean> propertiesBeans = new LinkedHashMap<>();
         applicationContext.getBeansWithAnnotation(ConfigurationProperties.class).forEach((name, instance) -> {
            ConfigurationPropertiesBean propertiesBean = get(applicationContext, instance, name);
            if (propertiesBean != null) {
               propertiesBeans.put(name, propertiesBean);
            }
         });
         return propertiesBeans;
      }
   }

   private static Map<String, ConfigurationPropertiesBean> getAll(ConfigurableApplicationContext applicationContext) {
      Map<String, ConfigurationPropertiesBean> propertiesBeans = new LinkedHashMap<>();
      ConfigurableListableBeanFactory beanFactory = applicationContext.getBeanFactory();
      Iterator<String> beanNames = beanFactory.getBeanNamesIterator();

      while (beanNames.hasNext()) {
         String beanName = beanNames.next();
         if (isConfigurationPropertiesBean(beanFactory, beanName)) {
            try {
               Object bean = beanFactory.getBean(beanName);
               ConfigurationPropertiesBean propertiesBean = get(applicationContext, bean, beanName);
               if (propertiesBean != null) {
                  propertiesBeans.put(beanName, propertiesBean);
               }
            } catch (Exception var7) {
            }
         }
      }

      return propertiesBeans;
   }

   private static boolean isConfigurationPropertiesBean(ConfigurableListableBeanFactory beanFactory, String beanName) {
      try {
         if (beanFactory.getBeanDefinition(beanName).isAbstract()) {
            return false;
         }

         if (beanFactory.findAnnotationOnBean(beanName, ConfigurationProperties.class) != null) {
            return true;
         }

         Method factoryMethod = findFactoryMethod(beanFactory, beanName);
         return findMergedAnnotation(factoryMethod, ConfigurationProperties.class).isPresent();
      } catch (NoSuchBeanDefinitionException ex) {
         return false;
      }
   }

   public static @Nullable ConfigurationPropertiesBean get(ApplicationContext applicationContext, Object bean, String beanName) {
      Method factoryMethod = findFactoryMethod(applicationContext, beanName);
      Bindable<Object> bindTarget = createBindTarget(bean, bean.getClass(), factoryMethod);
      if (bindTarget == null) {
         return null;
      }

      bindTarget = bindTarget.withBindMethod(BindMethodAttribute.get(applicationContext, beanName));
      if (bindTarget.getBindMethod() == null && factoryMethod != null) {
         bindTarget = bindTarget.withBindMethod(BindMethod.JAVA_BEAN);
      }

      if (bindTarget.getBindMethod() != BindMethod.VALUE_OBJECT) {
         bindTarget = bindTarget.withExistingValue(bean);
      }

      return create(beanName, bean, bindTarget);
   }

   private static @Nullable Method findFactoryMethod(ApplicationContext applicationContext, String beanName) {
      return applicationContext instanceof ConfigurableApplicationContext configurableContext ? findFactoryMethod(configurableContext, beanName) : null;
   }

   private static @Nullable Method findFactoryMethod(ConfigurableApplicationContext applicationContext, String beanName) {
      return findFactoryMethod(applicationContext.getBeanFactory(), beanName);
   }

   private static @Nullable Method findFactoryMethod(ConfigurableListableBeanFactory beanFactory, String beanName) {
      return beanFactory.containsBeanDefinition(beanName) && beanFactory.getMergedBeanDefinition(beanName) instanceof RootBeanDefinition rootBeanDefinition
         ? rootBeanDefinition.getResolvedFactoryMethod()
         : null;
   }

   static ConfigurationPropertiesBean forValueObject(Class<?> beanType, String beanName) {
      Bindable<Object> bindTarget = createBindTarget(null, beanType, null);
      Assert.state(
         bindTarget != null && deduceBindMethod(bindTarget) == BindMethod.VALUE_OBJECT,
         () -> "Bean '" + beanName + "' is not a @ConfigurationProperties value object"
      );
      return create(beanName, null, bindTarget.withBindMethod(BindMethod.VALUE_OBJECT));
   }

   private static @Nullable Bindable<Object> createBindTarget(@Nullable Object bean, Class<?> beanType, @Nullable Method factoryMethod) {
      ResolvableType type = factoryMethod != null ? ResolvableType.forMethodReturnType(factoryMethod) : ResolvableType.forClass(beanType);
      Annotation[] annotations = findAnnotations(bean, beanType, factoryMethod);
      return annotations != null ? Bindable.<Object>of(type).withAnnotations(annotations) : null;
   }

   private static Annotation @Nullable [] findAnnotations(@Nullable Object instance, Class<?> type, @Nullable Method factory) {
      ConfigurationProperties annotation = findAnnotation(instance, type, factory, ConfigurationProperties.class);
      if (annotation == null) {
         return null;
      }

      Validated validated = findAnnotation(instance, type, factory, Validated.class);
      return validated != null ? new Annotation[]{annotation, validated} : new Annotation[]{annotation};
   }

   private static <A extends Annotation> @Nullable A findAnnotation(@Nullable Object instance, Class<?> type, @Nullable Method factory, Class<A> annotationType) {
      MergedAnnotation<A> annotation = MergedAnnotation.missing();
      if (factory != null) {
         annotation = findMergedAnnotation(factory, annotationType);
      }

      if (!annotation.isPresent()) {
         annotation = findMergedAnnotation(type, annotationType);
      }

      if (!annotation.isPresent() && AopUtils.isAopProxy(instance)) {
         annotation = MergedAnnotations.from(AopUtils.getTargetClass(instance), SearchStrategy.TYPE_HIERARCHY).get(annotationType);
      }

      return (A)(annotation.isPresent() ? annotation.synthesize() : null);
   }

   private static <A extends Annotation> MergedAnnotation<A> findMergedAnnotation(@Nullable AnnotatedElement element, Class<A> annotationType) {
      return element != null ? MergedAnnotations.from(element, SearchStrategy.TYPE_HIERARCHY).get(annotationType) : MergedAnnotation.missing();
   }

   @Contract("_, _, !null -> !null")
   private static @Nullable ConfigurationPropertiesBean create(String name, @Nullable Object instance, @Nullable Bindable<Object> bindTarget) {
      return bindTarget != null ? new ConfigurationPropertiesBean(name, instance, bindTarget) : null;
   }

   static BindMethod deduceBindMethod(Class<?> type) {
      return deduceBindMethod(BindConstructorProvider.DEFAULT.getBindConstructor(type, false));
   }

   static BindMethod deduceBindMethod(Bindable<Object> bindable) {
      return deduceBindMethod(BindConstructorProvider.DEFAULT.getBindConstructor(bindable, false));
   }

   private static BindMethod deduceBindMethod(@Nullable Constructor<?> bindConstructor) {
      return bindConstructor != null ? BindMethod.VALUE_OBJECT : BindMethod.JAVA_BEAN;
   }
}
