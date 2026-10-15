package org.springframework.context.annotation;

import java.lang.reflect.Constructor;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.Aware;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.util.Assert;

abstract class ParserStrategyUtils {
   static <T> T instantiateClass(Class<?> clazz, Class<T> assignableTo, Environment environment, ResourceLoader resourceLoader, BeanDefinitionRegistry registry) {
      Assert.notNull(clazz, "Class must not be null");
      Assert.isAssignable(assignableTo, clazz);
      if (clazz.isInterface()) {
         throw new BeanInstantiationException(clazz, "Specified class is an interface");
      }

      ClassLoader classLoader = registry instanceof ConfigurableBeanFactory cbf ? cbf.getBeanClassLoader() : resourceLoader.getClassLoader();
      T instance = (T)createInstance(clazz, environment, resourceLoader, registry, classLoader);
      invokeAwareMethods(instance, environment, resourceLoader, registry, classLoader);
      return instance;
   }

   private static Object createInstance(
      Class<?> clazz, Environment environment, ResourceLoader resourceLoader, BeanDefinitionRegistry registry, ClassLoader classLoader
   ) {
      Constructor<?>[] constructors = clazz.getDeclaredConstructors();
      if (constructors.length == 1 && constructors[0].getParameterCount() > 0) {
         try {
            Constructor<?> constructor = constructors[0];
            Object[] args = resolveArgs(constructor.getParameterTypes(), environment, resourceLoader, registry, classLoader);
            return BeanUtils.instantiateClass(constructor, args);
         } catch (Exception ex) {
            throw new BeanInstantiationException(clazz, "No suitable constructor found", ex);
         }
      } else {
         return BeanUtils.instantiateClass(clazz);
      }
   }

   private static Object[] resolveArgs(
      Class<?>[] parameterTypes, Environment environment, ResourceLoader resourceLoader, BeanDefinitionRegistry registry, ClassLoader classLoader
   ) {
      Object[] parameters = new Object[parameterTypes.length];

      for (int i = 0; i < parameterTypes.length; i++) {
         parameters[i] = resolveParameter(parameterTypes[i], environment, resourceLoader, registry, classLoader);
      }

      return parameters;
   }

   private static @Nullable Object resolveParameter(
      Class<?> parameterType, Environment environment, ResourceLoader resourceLoader, BeanDefinitionRegistry registry, @Nullable ClassLoader classLoader
   ) {
      if (parameterType == Environment.class) {
         return environment;
      } else if (parameterType == ResourceLoader.class) {
         return resourceLoader;
      } else if (parameterType == BeanFactory.class) {
         return registry instanceof BeanFactory ? registry : null;
      } else if (parameterType == ClassLoader.class) {
         return classLoader;
      } else {
         throw new IllegalStateException("Illegal method parameter type: " + parameterType.getName());
      }
   }

   private static void invokeAwareMethods(
      Object parserStrategyBean, Environment environment, ResourceLoader resourceLoader, BeanDefinitionRegistry registry, @Nullable ClassLoader classLoader
   ) {
      if (parserStrategyBean instanceof Aware) {
         if (parserStrategyBean instanceof BeanClassLoaderAware beanClassLoaderAware && classLoader != null) {
            beanClassLoaderAware.setBeanClassLoader(classLoader);
         }

         if (parserStrategyBean instanceof BeanFactoryAware beanFactoryAware && registry instanceof BeanFactory beanFactory) {
            beanFactoryAware.setBeanFactory(beanFactory);
         }

         if (parserStrategyBean instanceof EnvironmentAware environmentAware) {
            environmentAware.setEnvironment(environment);
         }

         if (parserStrategyBean instanceof ResourceLoaderAware resourceLoaderAware) {
            resourceLoaderAware.setResourceLoader(resourceLoader);
         }
      }
   }
}
