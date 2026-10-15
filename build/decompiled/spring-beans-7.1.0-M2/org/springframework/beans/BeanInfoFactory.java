package org.springframework.beans;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import org.jspecify.annotations.Nullable;

public interface BeanInfoFactory {
   @Nullable BeanInfo getBeanInfo(Class<?> beanClass) throws IntrospectionException;
}
