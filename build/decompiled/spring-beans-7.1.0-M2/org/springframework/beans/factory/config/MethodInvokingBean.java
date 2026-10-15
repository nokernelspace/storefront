package org.springframework.beans.factory.config;

import java.lang.reflect.InvocationTargetException;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.support.ArgumentConvertingMethodInvoker;
import org.springframework.util.ClassUtils;

public class MethodInvokingBean extends ArgumentConvertingMethodInvoker implements BeanClassLoaderAware, BeanFactoryAware, InitializingBean {
   private @Nullable ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();
   private @Nullable ConfigurableBeanFactory beanFactory;

   @Override
   public void setBeanClassLoader(ClassLoader classLoader) {
      this.beanClassLoader = classLoader;
   }

   protected Class<?> resolveClassName(String className) throws ClassNotFoundException {
      return ClassUtils.forName(className, this.beanClassLoader);
   }

   @Override
   public void setBeanFactory(BeanFactory beanFactory) {
      if (beanFactory instanceof ConfigurableBeanFactory cbf) {
         this.beanFactory = cbf;
      }
   }

   @Override
   protected TypeConverter getDefaultTypeConverter() {
      return this.beanFactory != null ? this.beanFactory.getTypeConverter() : super.getDefaultTypeConverter();
   }

   @Override
   public void afterPropertiesSet() throws Exception {
      this.prepare();
      this.invokeWithTargetException();
   }

   protected @Nullable Object invokeWithTargetException() throws Exception {
      try {
         return this.invoke();
      } catch (InvocationTargetException ex) {
         if (ex.getTargetException() instanceof Exception exception) {
            throw exception;
         } else if (ex.getTargetException() instanceof Error error) {
            throw error;
         } else {
            throw ex;
         }
      }
   }
}
