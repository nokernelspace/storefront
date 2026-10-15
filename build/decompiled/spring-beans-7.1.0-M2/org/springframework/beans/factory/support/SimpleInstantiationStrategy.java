package org.springframework.beans.factory.support;

import java.lang.reflect.Constructor;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

public class SimpleInstantiationStrategy implements InstantiationStrategy {
   private static final ThreadLocal<Method> currentlyInvokedFactoryMethod = new ThreadLocal<>();

   public static @Nullable Method getCurrentlyInvokedFactoryMethod() {
      return currentlyInvokedFactoryMethod.get();
   }

   public static <T> T instantiateWithFactoryMethod(Method method, Supplier<T> instanceSupplier) {
      Method priorInvokedFactoryMethod = currentlyInvokedFactoryMethod.get();

      try {
         currentlyInvokedFactoryMethod.set(method);
         return instanceSupplier.get();
      } finally {
         if (priorInvokedFactoryMethod != null) {
            currentlyInvokedFactoryMethod.set(priorInvokedFactoryMethod);
         } else {
            currentlyInvokedFactoryMethod.remove();
         }
      }
   }

   @Override
   public Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner) {
      if (!bd.hasMethodOverrides()) {
         Constructor<?> constructorToUse;
         synchronized (bd.constructorArgumentLock) {
            constructorToUse = (Constructor<?>)bd.resolvedConstructorOrFactoryMethod;
            if (constructorToUse == null) {
               Class<?> clazz = bd.getBeanClass();
               if (clazz.isInterface()) {
                  throw new BeanInstantiationException(clazz, "Specified class is an interface");
               }

               try {
                  constructorToUse = clazz.getDeclaredConstructor();
                  bd.resolvedConstructorOrFactoryMethod = constructorToUse;
               } catch (Throwable ex) {
                  throw new BeanInstantiationException(clazz, "No default constructor found", ex);
               }
            }
         }

         return BeanUtils.instantiateClass(constructorToUse);
      } else {
         return this.instantiateWithMethodInjection(bd, beanName, owner);
      }
   }

   protected Object instantiateWithMethodInjection(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner) {
      throw new UnsupportedOperationException("Method Injection not supported in SimpleInstantiationStrategy");
   }

   @Override
   public Object instantiate(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner, Constructor<?> ctor, Object... args) {
      return !bd.hasMethodOverrides() ? BeanUtils.instantiateClass(ctor, args) : this.instantiateWithMethodInjection(bd, beanName, owner, ctor, args);
   }

   protected Object instantiateWithMethodInjection(
      RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner, @Nullable Constructor<?> ctor, Object... args
   ) {
      throw new UnsupportedOperationException("Method Injection not supported in SimpleInstantiationStrategy");
   }

   @Override
   public Object instantiate(
      RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner, @Nullable Object factoryBean, Method factoryMethod, @Nullable Object... args
   ) {
      return instantiateWithFactoryMethod(
         factoryMethod,
         () -> {
            try {
               ReflectionUtils.makeAccessible(factoryMethod);
               Object result = factoryMethod.invoke(factoryBean, args);
               if (result == null) {
                  result = new NullBean();
               }

               return result;
            } catch (IllegalArgumentException ex) {
               if (factoryBean != null && !factoryMethod.getDeclaringClass().isInstance(factoryBean)) {
                  throw new BeanInstantiationException(
                     factoryMethod,
                     "Illegal factory instance for factory method '" + factoryMethod.getName() + "'; instance: " + factoryBean.getClass().getName(),
                     ex
                  );
               } else {
                  throw new BeanInstantiationException(
                     factoryMethod,
                     "Illegal arguments to factory method '" + factoryMethod.getName() + "'; args: " + StringUtils.arrayToCommaDelimitedString(args),
                     ex
                  );
               }
            } catch (IllegalAccessException | InaccessibleObjectException ex) {
               throw new BeanInstantiationException(factoryMethod, "Cannot access factory method '" + factoryMethod.getName() + "'; is it public?", ex);
            } catch (InvocationTargetException ex) {
               String msg = "Factory method '" + factoryMethod.getName() + "' threw exception with message: " + ex.getTargetException().getMessage();
               if (bd.getFactoryBeanName() != null && owner instanceof ConfigurableBeanFactory cbf && cbf.isCurrentlyInCreation(bd.getFactoryBeanName())) {
                  msg = "Circular reference involving containing bean '"
                     + bd.getFactoryBeanName()
                     + "' - consider declaring the factory method as static for independence from its containing instance. "
                     + msg;
               }

               throw new BeanInstantiationException(factoryMethod, msg, ex.getTargetException());
            }
         }
      );
   }
}
