package org.springframework.beans.factory;

import java.lang.reflect.Type;
import org.springframework.beans.BeansException;
import org.springframework.core.ResolvableType;

public class BeanNotOfRequiredTypeException extends BeansException {
   private final String beanName;
   private final Type genericRequiredType;
   private final Class<?> actualType;

   public BeanNotOfRequiredTypeException(String beanName, Class<?> requiredType, Class<?> actualType) {
      this(beanName, (Type)requiredType, actualType);
   }

   public BeanNotOfRequiredTypeException(String beanName, Type requiredType, Class<?> actualType) {
      super(
         "Bean named '"
            + beanName
            + "' is expected to be of type '"
            + requiredType.getTypeName()
            + "' but was actually of type '"
            + actualType.getTypeName()
            + "'"
      );
      this.beanName = beanName;
      this.genericRequiredType = requiredType;
      this.actualType = actualType;
   }

   public String getBeanName() {
      return this.beanName;
   }

   public Class<?> getRequiredType() {
      return this.genericRequiredType instanceof Class<?> clazz ? clazz : ResolvableType.forType(this.genericRequiredType).toClass();
   }

   public Type getGenericRequiredType() {
      return this.genericRequiredType;
   }

   public Class<?> getActualType() {
      return this.actualType;
   }
}
