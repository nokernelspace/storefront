package org.springframework.beans.factory.support;

import org.springframework.util.ClassUtils;

record MethodDescriptor(Class<?> declaringClass, String methodName, Class<?>... parameterTypes) {
   static MethodDescriptor create(String beanName, Class<?> beanClass, String methodName) {
      try {
         Class<?> declaringClass = beanClass;
         String methodNameToUse = methodName;
         int indexOfDot = methodName.lastIndexOf(46);
         if (indexOfDot > 0) {
            String className = methodName.substring(0, indexOfDot);
            methodNameToUse = methodName.substring(indexOfDot + 1);
            if (!beanClass.getName().equals(className)) {
               declaringClass = ClassUtils.forName(className, beanClass.getClassLoader());
            }
         }

         return new MethodDescriptor(declaringClass, methodNameToUse);
      } catch (Exception | LinkageError ex) {
         throw new BeanDefinitionValidationException(
            "Could not create MethodDescriptor for method '%s' on bean with name '%s': %s".formatted(methodName, beanName, ex.getMessage())
         );
      }
   }
}
