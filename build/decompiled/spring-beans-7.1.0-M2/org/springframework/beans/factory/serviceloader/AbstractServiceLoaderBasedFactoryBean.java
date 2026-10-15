package org.springframework.beans.factory.serviceloader;

import java.util.ServiceLoader;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.config.AbstractFactoryBean;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

public abstract class AbstractServiceLoaderBasedFactoryBean extends AbstractFactoryBean<Object> implements BeanClassLoaderAware {
   private @Nullable Class<?> serviceType;
   private @Nullable ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

   public void setServiceType(@Nullable Class<?> serviceType) {
      this.serviceType = serviceType;
   }

   public @Nullable Class<?> getServiceType() {
      return this.serviceType;
   }

   @Override
   public void setBeanClassLoader(@Nullable ClassLoader beanClassLoader) {
      this.beanClassLoader = beanClassLoader;
   }

   @Override
   protected Object createInstance() {
      Assert.state(this.getServiceType() != null, "Property 'serviceType' is required");
      return this.getObjectToExpose(ServiceLoader.load(this.getServiceType(), this.beanClassLoader));
   }

   protected abstract Object getObjectToExpose(ServiceLoader<?> serviceLoader);
}
