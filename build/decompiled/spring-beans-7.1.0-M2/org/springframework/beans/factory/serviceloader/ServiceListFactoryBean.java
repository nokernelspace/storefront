package org.springframework.beans.factory.serviceloader;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.springframework.beans.factory.BeanClassLoaderAware;

public class ServiceListFactoryBean extends AbstractServiceLoaderBasedFactoryBean implements BeanClassLoaderAware {
   @Override
   protected Object getObjectToExpose(ServiceLoader<?> serviceLoader) {
      List<Object> result = new ArrayList<>();

      for (Object loaderObject : serviceLoader) {
         result.add(loaderObject);
      }

      return result;
   }

   @Override
   public Class<?> getObjectType() {
      return List.class;
   }
}
