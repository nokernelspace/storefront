package org.springframework.beans.factory;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.core.ResolvableType;

public interface ListableBeanFactory extends BeanFactory {
   boolean containsBeanDefinition(String beanName);

   int getBeanDefinitionCount();

   String[] getBeanDefinitionNames();

   <T> ObjectProvider<T> getBeanProvider(Class<T> requiredType, boolean allowEagerInit);

   <T> ObjectProvider<T> getBeanProvider(ResolvableType requiredType, boolean allowEagerInit);

   String[] getBeanNamesForType(ResolvableType type);

   String[] getBeanNamesForType(ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit);

   String[] getBeanNamesForType(@Nullable Class<?> type);

   String[] getBeanNamesForType(@Nullable Class<?> type, boolean includeNonSingletons, boolean allowEagerInit);

   <T> Map<String, T> getBeansOfType(@Nullable Class<T> type) throws BeansException;

   <T> Map<String, T> getBeansOfType(@Nullable Class<T> type, boolean includeNonSingletons, boolean allowEagerInit) throws BeansException;

   String[] getBeanNamesForAnnotation(Class<? extends Annotation> annotationType);

   Map<String, Object> getBeansWithAnnotation(Class<? extends Annotation> annotationType) throws BeansException;

   <A extends Annotation> @Nullable A findAnnotationOnBean(String beanName, Class<A> annotationType) throws NoSuchBeanDefinitionException;

   <A extends Annotation> @Nullable A findAnnotationOnBean(String beanName, Class<A> annotationType, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException;

   <A extends Annotation> Set<A> findAllAnnotationsOnBean(String beanName, Class<A> annotationType, boolean allowFactoryBeanInit) throws NoSuchBeanDefinitionException;
}
