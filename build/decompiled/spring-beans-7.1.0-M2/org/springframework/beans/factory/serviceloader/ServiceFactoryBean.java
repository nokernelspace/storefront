package org.springframework.beans.factory.serviceloader;

import java.util.Iterator;
import java.util.ServiceLoader;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanClassLoaderAware;

public class ServiceFactoryBean extends AbstractServiceLoaderBasedFactoryBean implements BeanClassLoaderAware {
   @Override
   protected Object getObjectToExpose(ServiceLoader<?> serviceLoader) {
      Iterator<?> it = serviceLoader.iterator();
      if (!it.hasNext()) {
         throw new IllegalStateException("ServiceLoader could not find service for type [" + this.getServiceType() + "]");
      } else {
         return it.next();
      }
   }

   @Override
   public @Nullable Class<?> getObjectType() {
      return this.getServiceType();
   }
}
