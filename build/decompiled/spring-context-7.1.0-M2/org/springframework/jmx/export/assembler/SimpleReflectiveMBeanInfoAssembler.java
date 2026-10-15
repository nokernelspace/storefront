package org.springframework.jmx.export.assembler;

import java.lang.reflect.Method;

public class SimpleReflectiveMBeanInfoAssembler extends AbstractConfigurableMBeanInfoAssembler {
   @Override
   protected boolean includeReadAttribute(Method method, String beanKey) {
      return true;
   }

   @Override
   protected boolean includeWriteAttribute(Method method, String beanKey) {
      return true;
   }

   @Override
   protected boolean includeOperation(Method method, String beanKey) {
      return true;
   }
}
