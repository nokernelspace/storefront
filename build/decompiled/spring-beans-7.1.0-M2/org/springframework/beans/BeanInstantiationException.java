package org.springframework.beans;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.jspecify.annotations.Nullable;

public class BeanInstantiationException extends FatalBeanException {
   private final Class<?> beanClass;
   private final @Nullable Constructor<?> constructor;
   private final @Nullable Method constructingMethod;

   public BeanInstantiationException(Class<?> beanClass, String msg) {
      this(beanClass, msg, null);
   }

   public BeanInstantiationException(Class<?> beanClass, String msg, @Nullable Throwable cause) {
      super("Failed to instantiate [" + beanClass.getName() + "]: " + msg, cause);
      this.beanClass = beanClass;
      this.constructor = null;
      this.constructingMethod = null;
   }

   public BeanInstantiationException(Constructor<?> constructor, @Nullable String msg, @Nullable Throwable cause) {
      super("Failed to instantiate [" + constructor.getDeclaringClass().getName() + "]: " + msg, cause);
      this.beanClass = constructor.getDeclaringClass();
      this.constructor = constructor;
      this.constructingMethod = null;
   }

   public BeanInstantiationException(Method constructingMethod, @Nullable String msg, @Nullable Throwable cause) {
      super("Failed to instantiate [" + constructingMethod.getReturnType().getName() + "]: " + msg, cause);
      this.beanClass = constructingMethod.getReturnType();
      this.constructor = null;
      this.constructingMethod = constructingMethod;
   }

   public Class<?> getBeanClass() {
      return this.beanClass;
   }

   public @Nullable Constructor<?> getConstructor() {
      return this.constructor;
   }

   public @Nullable Method getConstructingMethod() {
      return this.constructingMethod;
   }
}
