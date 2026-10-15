package org.springframework.core.type;

import java.lang.reflect.Method;

public interface MethodMetadata extends AnnotatedTypeMetadata {
   String getMethodName();

   String getDeclaringClassName();

   String getReturnTypeName();

   boolean isAbstract();

   boolean isStatic();

   boolean isFinal();

   boolean isOverridable();

   static MethodMetadata introspect(Method method) {
      return StandardMethodMetadata.from(method);
   }
}
