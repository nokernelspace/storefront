package org.springframework.beans.factory.aot;

import org.springframework.aot.generate.GeneratedMethods;
import org.springframework.aot.generate.MethodReference;
import org.springframework.javapoet.ClassName;

public interface BeanRegistrationCode {
   ClassName getClassName();

   GeneratedMethods getMethods();

   void addInstancePostProcessor(MethodReference methodReference);
}
