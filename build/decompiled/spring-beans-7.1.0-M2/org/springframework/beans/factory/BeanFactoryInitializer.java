package org.springframework.beans.factory;

public interface BeanFactoryInitializer<F extends ListableBeanFactory> {
   void initialize(F beanFactory);
}
