package org.springframework.beans.factory.aot;

import org.springframework.aot.generate.GeneratedMethods;
import org.springframework.javapoet.ClassName;

public interface BeanRegistrationsCode {
   ClassName getClassName();

   GeneratedMethods getMethods();
}
