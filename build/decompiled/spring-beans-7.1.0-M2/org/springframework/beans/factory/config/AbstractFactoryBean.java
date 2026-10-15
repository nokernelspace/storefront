package org.springframework.beans.factory.config;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.SimpleTypeConverter;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.FactoryBeanNotInitializedException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

public abstract class AbstractFactoryBean<T> implements FactoryBean<T>, BeanClassLoaderAware, BeanFactoryAware, InitializingBean, DisposableBean {
   protected final Log logger = LogFactory.getLog(this.getClass());
   private boolean singleton = true;
   private @Nullable ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();
   private @Nullable BeanFactory beanFactory;
   private @Nullable T singletonInstance;
   private @Nullable T earlySingletonInstance;

   public void setSingleton(boolean singleton) {
      this.singleton = singleton;
   }

   @Override
   public boolean isSingleton() {
      return this.singleton;
   }

   @Override
   public void setBeanClassLoader(ClassLoader classLoader) {
      this.beanClassLoader = classLoader;
   }

   @Override
   public void setBeanFactory(@Nullable BeanFactory beanFactory) {
      this.beanFactory = beanFactory;
   }

   protected @Nullable BeanFactory getBeanFactory() {
      return this.beanFactory;
   }

   protected TypeConverter getBeanTypeConverter() {
      return this.getBeanFactory() instanceof ConfigurableBeanFactory cbf ? cbf.getTypeConverter() : new SimpleTypeConverter();
   }

   @Override
   public void afterPropertiesSet() throws Exception {
      if (this.isSingleton()) {
         this.singletonInstance = this.createInstance();
         this.earlySingletonInstance = null;
      }
   }

   @Override
   public final T getObject() throws Exception {
      if (this.isSingleton()) {
         T instance = this.singletonInstance;
         return instance != null ? instance : this.getEarlySingletonInstance();
      } else {
         return this.createInstance();
      }
   }

   private T getEarlySingletonInstance() {
      Class<?>[] ifcs = this.getEarlySingletonInterfaces();
      if (ifcs == null) {
         throw new FactoryBeanNotInitializedException(this.getClass().getName() + " does not support circular references");
      }

      if (this.earlySingletonInstance == null) {
         this.earlySingletonInstance = (T)Proxy.newProxyInstance(this.beanClassLoader, ifcs, new AbstractFactoryBean.EarlySingletonInvocationHandler());
      }

      return this.earlySingletonInstance;
   }

   private T getSingletonInstance() throws IllegalStateException {
      T instance = this.singletonInstance;
      Assert.state(instance != null, "Singleton instance not initialized yet");
      return instance;
   }

   @Override
   public void destroy() throws Exception {
      if (this.isSingleton()) {
         T instance = this.singletonInstance;
         if (instance != null) {
            this.destroyInstance(instance);
         }
      }
   }

   @Override
   public abstract @Nullable Class<?> getObjectType();

   protected abstract T createInstance() throws Exception;

   protected Class<?> @Nullable [] getEarlySingletonInterfaces() {
      Class<?> type = this.getObjectType();
      return type != null && type.isInterface() ? new Class[]{type} : null;
   }

   protected void destroyInstance(T instance) throws Exception {
   }

   private class EarlySingletonInvocationHandler implements InvocationHandler {
      @Override
      public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
         if (ReflectionUtils.isEqualsMethod(method)) {
            return proxy == args[0];
         }

         if (ReflectionUtils.isHashCodeMethod(method)) {
            return System.identityHashCode(proxy);
         }

         if (ReflectionUtils.isToStringMethod(method) && AbstractFactoryBean.this.singletonInstance == null) {
            return "Early singleton proxy for interfaces " + ObjectUtils.nullSafeToString(AbstractFactoryBean.this.getEarlySingletonInterfaces());
         }

         try {
            return method.invoke(AbstractFactoryBean.this.getSingletonInstance(), args);
         } catch (InvocationTargetException ex) {
            throw ex.getTargetException();
         }
      }
   }
}
