package org.springframework.beans.factory;

import org.jspecify.annotations.Nullable;

public interface HierarchicalBeanFactory extends BeanFactory {
   @Nullable BeanFactory getParentBeanFactory();

   boolean containsLocalBean(String name);
}
