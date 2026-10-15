package org.springframework.beans.factory.support;

import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.BeanIsNotAFactoryException;
import org.springframework.beans.factory.BeanNotOfRequiredTypeException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public class StaticListableBeanFactory implements ListableBeanFactory {
   private final Map<String, Object> beans;

   public StaticListableBeanFactory() {
      this.beans = new LinkedHashMap<>();
   }

   public StaticListableBeanFactory(Map<String, Object> beans) {
      Assert.notNull(beans, "Beans Map must not be null");
      this.beans = beans;
   }

   public void addBean(String name, Object bean) {
      this.beans.put(name, bean);
   }

   @Override
   public Object getBean(String name) throws BeansException {
      return this.getBean(name, (Class)null);
   }

   @Override
   public <T> T getBean(String name, @Nullable Class<T> requiredType) throws BeansException {
      String beanName = BeanFactoryUtils.transformedBeanName(name);
      Object bean = this.obtainBean(beanName);
      if (BeanFactoryUtils.isFactoryDereference(name)) {
         if (!(bean instanceof FactoryBean)) {
            throw new BeanIsNotAFactoryException(beanName, bean.getClass());
         }
      } else if (bean instanceof FactoryBean<?> factoryBean) {
         try {
            Object exposedObject = factoryBean instanceof SmartFactoryBean<?> smartFactoryBean && requiredType != null
               ? smartFactoryBean.getObject(requiredType)
               : factoryBean.getObject();
            if (exposedObject == null) {
               throw new BeanCreationException(beanName, "FactoryBean exposed null object");
            }

            bean = exposedObject;
         } catch (Exception ex) {
            throw new BeanCreationException(beanName, "FactoryBean threw exception on object creation", ex);
         }
      }

      if (requiredType != null && !requiredType.isInstance(bean)) {
         throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
      } else {
         return (T)bean;
      }
   }

   @Override
   public <T> T getBean(String name, ParameterizedTypeReference<T> typeReference) throws BeansException {
      Object bean = this.getBean(name);
      Type requiredType = typeReference.getType();
      if (!ResolvableType.forType(requiredType).isInstance(bean)) {
         throw new BeanNotOfRequiredTypeException(name, requiredType, bean.getClass());
      } else {
         return (T)bean;
      }
   }

   @Override
   public Object getBean(String name, @Nullable Object @Nullable ... args) throws BeansException {
      if (!ObjectUtils.isEmpty(args)) {
         throw new UnsupportedOperationException("StaticListableBeanFactory does not support explicit bean creation arguments");
      } else {
         return this.getBean(name);
      }
   }

   private Object obtainBean(String beanName) {
      Object bean = this.beans.get(beanName);
      if (bean == null) {
         throw new NoSuchBeanDefinitionException(beanName, "Defined beans are [" + StringUtils.collectionToCommaDelimitedString(this.beans.keySet()) + "]");
      } else {
         return bean;
      }
   }

   @Override
   public <T> T getBean(Class<T> requiredType) throws BeansException {
      String[] beanNames = this.getBeanNamesForType(requiredType);
      if (beanNames.length == 1) {
         return this.getBean(beanNames[0], requiredType);
      } else if (beanNames.length > 1) {
         throw new NoUniqueBeanDefinitionException(requiredType, beanNames);
      } else {
         throw new NoSuchBeanDefinitionException(requiredType);
      }
   }

   @Override
   public <T> T getBean(Class<T> requiredType, @Nullable Object @Nullable ... args) throws BeansException {
      if (!ObjectUtils.isEmpty(args)) {
         throw new UnsupportedOperationException("StaticListableBeanFactory does not support explicit bean creation arguments");
      } else {
         return this.getBean(requiredType);
      }
   }

   @Override
   public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType) throws BeansException {
      return this.getBeanProvider(ResolvableType.forRawClass(requiredType), true);
   }

   @Override
   public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType) {
      return this.getBeanProvider(requiredType, true);
   }

   @Override
   public <T> ObjectProvider<T> getBeanProvider(ParameterizedTypeReference<T> requiredType) {
      return this.getBeanProvider(ResolvableType.forType(requiredType), true);
   }

   @Override
   public boolean containsBean(String name) {
      return this.beans.containsKey(name);
   }

   @Override
   public boolean isSingleton(String name) throws NoSuchBeanDefinitionException {
      String beanName = BeanFactoryUtils.transformedBeanName(name);
      return this.obtainBean(beanName) instanceof FactoryBean<?> factoryBean && !BeanFactoryUtils.isFactoryDereference(name) ? factoryBean.isSingleton() : true;
   }

   @Override
   public boolean isPrototype(String name) throws NoSuchBeanDefinitionException {
      String beanName = BeanFactoryUtils.transformedBeanName(name);
      Object bean = this.obtainBean(beanName);
      return !BeanFactoryUtils.isFactoryDereference(name)
         && (
            bean instanceof SmartFactoryBean<?> smartFactoryBean && smartFactoryBean.isPrototype()
               || bean instanceof FactoryBean<?> factoryBean && !factoryBean.isSingleton()
         );
   }

   @Override
   public boolean isTypeMatch(String name, ResolvableType typeToMatch) throws NoSuchBeanDefinitionException {
      String beanName = BeanFactoryUtils.transformedBeanName(name);
      Object bean = this.obtainBean(beanName);
      if (bean instanceof FactoryBean<?> factoryBean && !BeanFactoryUtils.isFactoryDereference(name)) {
         Class<?> classToMatch = typeToMatch.resolve();
         return classToMatch != null && this.isTypeMatch(factoryBean, classToMatch);
      } else {
         return typeToMatch.isInstance(bean);
      }
   }

   @Override
   public boolean isTypeMatch(String name, Class<?> typeToMatch) throws NoSuchBeanDefinitionException {
      String beanName = BeanFactoryUtils.transformedBeanName(name);
      Object bean = this.obtainBean(beanName);
      return bean instanceof FactoryBean<?> factoryBean && !BeanFactoryUtils.isFactoryDereference(name)
         ? this.isTypeMatch(factoryBean, typeToMatch)
         : typeToMatch.isInstance(bean);
   }

   private boolean isTypeMatch(FactoryBean<?> factoryBean, Class<?> typeToMatch) throws NoSuchBeanDefinitionException {
      if (factoryBean instanceof SmartFactoryBean<?> smartFactoryBean) {
         return smartFactoryBean.supportsType(typeToMatch);
      } else {
         Class<?> objectType = factoryBean.getObjectType();
         return objectType != null && typeToMatch.isAssignableFrom(objectType);
      }
   }

   @Override
   public @Nullable Class<?> getType(String name) throws NoSuchBeanDefinitionException {
      return this.getType(name, true);
   }

   @Override
   public @Nullable Class<?> getType(String name, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
      String beanName = BeanFactoryUtils.transformedBeanName(name);
      Object bean = this.obtainBean(beanName);
      return bean instanceof FactoryBean<?> factoryBean && !BeanFactoryUtils.isFactoryDereference(name) ? factoryBean.getObjectType() : bean.getClass();
   }

   @Override
   public String[] getAliases(String name) {
      return new String[0];
   }

   @Override
   public boolean containsBeanDefinition(String name) {
      return this.beans.containsKey(name);
   }

   @Override
   public int getBeanDefinitionCount() {
      return this.beans.size();
   }

   @Override
   public String[] getBeanDefinitionNames() {
      return StringUtils.toStringArray(this.beans.keySet());
   }

   @Override
   public <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType, boolean allowEagerInit) {
      return this.getBeanProvider(ResolvableType.forRawClass(requiredType), allowEagerInit);
   }

   @Override
   public <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType, boolean allowEagerInit) {
      return new ObjectProvider<T>() {
         @Override
         public T getObject() throws BeansException {
            String[] beanNames = StaticListableBeanFactory.this.getBeanNamesForType(requiredType);
            if (beanNames.length == 1) {
               return StaticListableBeanFactory.this.getBean(beanNames[0], requiredType.toClass());
            } else if (beanNames.length > 1) {
               throw new NoUniqueBeanDefinitionException(requiredType, beanNames);
            } else {
               throw new NoSuchBeanDefinitionException(requiredType);
            }
         }

         @Override
         public T getObject(@Nullable Object... args) throws BeansException {
            String[] beanNames = StaticListableBeanFactory.this.getBeanNamesForType(requiredType);
            if (beanNames.length == 1) {
               return (T)StaticListableBeanFactory.this.getBean(beanNames[0], args);
            } else if (beanNames.length > 1) {
               throw new NoUniqueBeanDefinitionException(requiredType, beanNames);
            } else {
               throw new NoSuchBeanDefinitionException(requiredType);
            }
         }

         @Override
         public @Nullable T getIfAvailable() throws BeansException {
            String[] beanNames = StaticListableBeanFactory.this.getBeanNamesForType(requiredType);
            if (beanNames.length == 1) {
               return StaticListableBeanFactory.this.getBean(beanNames[0], requiredType.toClass());
            } else if (beanNames.length > 1) {
               throw new NoUniqueBeanDefinitionException(requiredType, beanNames);
            } else {
               return null;
            }
         }

         @Override
         public @Nullable T getIfUnique() throws BeansException {
            String[] beanNames = StaticListableBeanFactory.this.getBeanNamesForType(requiredType);
            return beanNames.length == 1 ? StaticListableBeanFactory.this.getBean(beanNames[0], requiredType.toClass()) : null;
         }

         @Override
         public Stream<T> stream() {
            return Arrays.stream(StaticListableBeanFactory.this.getBeanNamesForType(requiredType))
               .map(name -> StaticListableBeanFactory.this.getBean(name, requiredType.toClass()));
         }
      };
   }

   @Override
   public String[] getBeanNamesForType(@Nullable ResolvableType type) {
      return this.getBeanNamesForType(type, true, true);
   }

   @Override
   public String[] getBeanNamesForType(@Nullable ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit) {
      Class<?> clazz = type != null ? type.resolve() : null;
      boolean isFactoryType = clazz != null && FactoryBean.class.isAssignableFrom(clazz);
      List<String> matches = new ArrayList<>();

      for (Entry<String, Object> entry : this.beans.entrySet()) {
         String beanName = entry.getKey();
         Object beanInstance = entry.getValue();
         if (beanInstance instanceof FactoryBean<?> factoryBean && !isFactoryType) {
            if ((includeNonSingletons || factoryBean.isSingleton()) && (type == null || clazz != null && this.isTypeMatch(factoryBean, clazz))) {
               matches.add(beanName);
            }
         } else if (type == null || type.isInstance(beanInstance)) {
            matches.add(beanName);
         }
      }

      return StringUtils.toStringArray(matches);
   }

   @Override
   public String[] getBeanNamesForType(@Nullable Class<?> type) {
      return this.getBeanNamesForType(ResolvableType.forClass(type));
   }

   @Override
   public String[] getBeanNamesForType(@Nullable Class<?> type, boolean includeNonSingletons, boolean allowEagerInit) {
      return this.getBeanNamesForType(ResolvableType.forClass(type), includeNonSingletons, allowEagerInit);
   }

   @Override
   public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type) throws BeansException {
      return this.getBeansOfType(type, true, true);
   }

   @Override
   public <T> Map<String, T> getBeansOfType(@Nullable Class<T> type, boolean includeNonSingletons, boolean allowEagerInit) throws BeansException {
      boolean isFactoryType = type != null && FactoryBean.class.isAssignableFrom(type);
      Map<String, T> matches = new LinkedHashMap<>();

      for (Entry<String, Object> entry : this.beans.entrySet()) {
         String beanName = entry.getKey();
         Object beanInstance = entry.getValue();
         if (beanInstance instanceof FactoryBean<?> factoryBean && !isFactoryType) {
            if ((includeNonSingletons || factoryBean.isSingleton()) && (type == null || this.isTypeMatch(factoryBean, type))) {
               matches.put(beanName, this.getBean(beanName, type));
            }
         } else if (type == null || type.isInstance(beanInstance)) {
            if (isFactoryType) {
               beanName = "&" + beanName;
            }

            matches.put(beanName, (T)beanInstance);
         }
      }

      return matches;
   }

   @Override
   public String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType) {
      List<String> results = new ArrayList<>();

      for (String beanName : this.beans.keySet()) {
         if (this.findAnnotationOnBean(beanName, annotationType) != null) {
            results.add(beanName);
         }
      }

      return StringUtils.toStringArray(results);
   }

   @Override
   public Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType) throws BeansException {
      Map<String, Object> results = new LinkedHashMap<>();

      for (String beanName : this.beans.keySet()) {
         if (this.findAnnotationOnBean(beanName, annotationType) != null) {
            results.put(beanName, this.getBean(beanName));
         }
      }

      return results;
   }

   @Override
   public <A extends Annotation> @Nullable A findAnnotationOnBean(String beanName, Class<A> annotationType) throws NoSuchBeanDefinitionException {
      return this.findAnnotationOnBean(beanName, annotationType, true);
   }

   @Override
   public <A extends Annotation> @Nullable A findAnnotationOnBean(String beanName, Class<A> annotationType, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
      Class<?> beanType = this.getType(beanName, allowFactoryBeanInit);
      return (A)(beanType != null ? AnnotatedElementUtils.findMergedAnnotation(beanType, annotationType) : null);
   }

   @Override
   public <A extends Annotation> Set<A> findAllAnnotationsOnBean(String beanName, Class<A> annotationType, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException {
      Class<?> beanType = this.getType(beanName, allowFactoryBeanInit);
      return beanType != null ? AnnotatedElementUtils.findAllMergedAnnotations(beanType, annotationType) : Collections.emptySet();
   }
}
