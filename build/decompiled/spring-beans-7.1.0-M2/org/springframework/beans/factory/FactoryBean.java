package org.springframework.beans.factory;

import org.jspecify.annotations.Nullable;

public interface FactoryBean<T> {
   String OBJECT_TYPE_ATTRIBUTE = "factoryBeanObjectType";

   @Nullable T getObject() throws Exception;

   @Nullable Class<?> getObjectType();

   default boolean isSingleton() {
      return true;
   }
}
