package org.springframework.beans.factory.config;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Properties;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.FatalBeanException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

public class ServiceLocatorFactoryBean implements FactoryBean<Object>, BeanFactoryAware, InitializingBean {
   private @Nullable Class<?> serviceLocatorInterface;
   private @Nullable Constructor<Exception> serviceLocatorExceptionConstructor;
   private @Nullable Properties serviceMappings;
   private @Nullable ListableBeanFactory beanFactory;
   private @Nullable Object proxy;

   public void setServiceLocatorInterface(Class<?> interfaceType) {
      this.serviceLocatorInterface = interfaceType;
   }

   public void setServiceLocatorExceptionClass(Class<? extends Exception> serviceLocatorExceptionClass) {
      this.serviceLocatorExceptionConstructor = this.determineServiceLocatorExceptionConstructor(serviceLocatorExceptionClass);
   }

   public void setServiceMappings(Properties serviceMappings) {
      this.serviceMappings = serviceMappings;
   }

   @Override
   public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
      if (beanFactory instanceof ListableBeanFactory lbf) {
         this.beanFactory = lbf;
      } else {
         throw new FatalBeanException("ServiceLocatorFactoryBean needs to run in a BeanFactory that is a ListableBeanFactory");
      }
   }

   @Override
   public void afterPropertiesSet() {
      if (this.serviceLocatorInterface == null) {
         throw new IllegalArgumentException("Property 'serviceLocatorInterface' is required");
      }

      this.proxy = Proxy.newProxyInstance(
         this.serviceLocatorInterface.getClassLoader(),
         new Class[]{this.serviceLocatorInterface},
         new ServiceLocatorFactoryBean.ServiceLocatorInvocationHandler()
      );
   }

   protected Constructor<Exception> determineServiceLocatorExceptionConstructor(Class<? extends Exception> exceptionClass) {
      try {
         return exceptionClass.getConstructor(String.class, Throwable.class);
      } catch (NoSuchMethodException ex) {
         try {
            return exceptionClass.getConstructor(Throwable.class);
         } catch (NoSuchMethodException ex2) {
            try {
               return exceptionClass.getConstructor(String.class);
            } catch (NoSuchMethodException ex3) {
               throw new IllegalArgumentException(
                  "Service locator exception [" + exceptionClass.getName() + "] neither has a (String, Throwable) constructor nor a (String) constructor"
               );
            }
         }
      }
   }

   protected Exception createServiceLocatorException(Constructor<Exception> exceptionConstructor, BeansException cause) {
      Class<?>[] paramTypes = exceptionConstructor.getParameterTypes();
      Object[] args = new Object[paramTypes.length];

      for (int i = 0; i < paramTypes.length; i++) {
         if (String.class == paramTypes[i]) {
            args[i] = cause.getMessage();
         } else if (paramTypes[i].isInstance(cause)) {
            args[i] = cause;
         }
      }

      return BeanUtils.instantiateClass(exceptionConstructor, args);
   }

   @Override
   public @Nullable Object getObject() {
      return this.proxy;
   }

   @Override
   public @Nullable Class<?> getObjectType() {
      return this.serviceLocatorInterface;
   }

   @Override
   public boolean isSingleton() {
      return true;
   }

   private class ServiceLocatorInvocationHandler implements InvocationHandler {
      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
         if (ReflectionUtils.isEqualsMethod(method)) {
            return proxy == args[0];
         } else if (ReflectionUtils.isHashCodeMethod(method)) {
            return System.identityHashCode(proxy);
         } else {
            return ReflectionUtils.isToStringMethod(method)
               ? "Service locator: " + ServiceLocatorFactoryBean.this.serviceLocatorInterface
               : this.invokeServiceLocatorMethod(method, args);
         }
      }

      private Object invokeServiceLocatorMethod(Method method, Object[] args) throws Exception {
         Class<?> serviceLocatorMethodReturnType = this.getServiceLocatorMethodReturnType(method);

         try {
            String beanName = this.tryGetBeanName(args);
            Assert.state(ServiceLocatorFactoryBean.this.beanFactory != null, "No BeanFactory available");
            return StringUtils.hasLength(beanName)
               ? ServiceLocatorFactoryBean.this.beanFactory.getBean(beanName, serviceLocatorMethodReturnType)
               : ServiceLocatorFactoryBean.this.beanFactory.getBean(serviceLocatorMethodReturnType);
         } catch (BeansException ex) {
            if (ServiceLocatorFactoryBean.this.serviceLocatorExceptionConstructor != null) {
               throw ServiceLocatorFactoryBean.this.createServiceLocatorException(ServiceLocatorFactoryBean.this.serviceLocatorExceptionConstructor, ex);
            } else {
               throw ex;
            }
         }
      }

      private String tryGetBeanName(Object @Nullable [] args) {
         String beanName = "";
         if (args != null && args.length == 1 && args[0] != null) {
            beanName = args[0].toString();
         }

         if (ServiceLocatorFactoryBean.this.serviceMappings != null) {
            String mappedName = ServiceLocatorFactoryBean.this.serviceMappings.getProperty(beanName);
            if (mappedName != null) {
               beanName = mappedName;
            }
         }

         return beanName;
      }

      private Class<?> getServiceLocatorMethodReturnType(Method method) throws NoSuchMethodException {
         Assert.state(ServiceLocatorFactoryBean.this.serviceLocatorInterface != null, "No service locator interface specified");
         Class<?>[] paramTypes = method.getParameterTypes();
         Method interfaceMethod = ServiceLocatorFactoryBean.this.serviceLocatorInterface.getMethod(method.getName(), paramTypes);
         Class<?> serviceLocatorReturnType = interfaceMethod.getReturnType();
         if (paramTypes.length <= 1 && void.class != serviceLocatorReturnType) {
            return serviceLocatorReturnType;
         } else {
            throw new UnsupportedOperationException(
               "May only call methods with signature '<type> xxx()' or '<type> xxx(<idtype> id)' on factory interface, but tried to call: " + interfaceMethod
            );
         }
      }
   }
}
