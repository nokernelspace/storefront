package org.springframework.beans.factory;

import org.jspecify.annotations.Nullable;

public interface SmartFactoryBean<T> extends FactoryBean<T> {
   default <S> @Nullable S getObject(Class<S> type) throws Exception {
      Class<?> objectType = this.getObjectType();
      return (S)(objectType != null && type.isAssignableFrom(objectType) ? this.getObject() : null);
   }

   default boolean supportsType(Class<?> type) {
      Class<?> objectType = this.getObjectType();
      return objectType != null && type.isAssignableFrom(objectType);
   }

   default boolean isPrototype() {
      return false;
   }

   default boolean isEagerInit() {
      return false;
   }
}
