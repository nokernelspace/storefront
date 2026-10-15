package org.springframework.beans.factory.aot;

import org.springframework.aot.generate.GeneratedMethods;
import org.springframework.aot.generate.MethodReference;
import org.springframework.javapoet.ClassName;

public interface BeanFactoryInitializationCode {
   String BEAN_FACTORY_VARIABLE = "beanFactory";

   GeneratedMethods getMethods();

   ClassName getClassName();

   void addInitializer(MethodReference methodReference);
}
