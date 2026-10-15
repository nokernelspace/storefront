package org.springframework.beans;

import java.beans.PropertyDescriptor;

public interface BeanWrapper extends ConfigurablePropertyAccessor {
   @Override
   void setAutoGrowCollectionLimit(int autoGrowCollectionLimit);

   @Override
   int getAutoGrowCollectionLimit();

   Object getWrappedInstance();

   Class<?> getWrappedClass();

   PropertyDescriptor[] getPropertyDescriptors();

   PropertyDescriptor getPropertyDescriptor(String propertyName) throws InvalidPropertyException;
}
