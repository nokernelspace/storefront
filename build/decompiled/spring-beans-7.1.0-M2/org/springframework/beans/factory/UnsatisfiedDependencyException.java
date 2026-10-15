package org.springframework.beans.factory;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.util.StringUtils;

public class UnsatisfiedDependencyException extends BeanCreationException {
   private final @Nullable InjectionPoint injectionPoint;

   public UnsatisfiedDependencyException(@Nullable String resourceDescription, @Nullable String beanName, String propertyName, @Nullable String msg) {
      super(
         resourceDescription,
         beanName,
         "Unsatisfied dependency expressed through bean property '" + propertyName + "'" + (StringUtils.hasLength(msg) ? ": " + msg : "")
      );
      this.injectionPoint = null;
   }

   public UnsatisfiedDependencyException(@Nullable String resourceDescription, @Nullable String beanName, String propertyName, BeansException ex) {
      this(resourceDescription, beanName, propertyName, ex.getMessage());
      this.initCause(ex);
   }

   public UnsatisfiedDependencyException(
      @Nullable String resourceDescription, @Nullable String beanName, @Nullable InjectionPoint injectionPoint, @Nullable String msg
   ) {
      super(resourceDescription, beanName, "Unsatisfied dependency expressed through " + injectionPoint + (StringUtils.hasLength(msg) ? ": " + msg : ""));
      this.injectionPoint = injectionPoint;
   }

   public UnsatisfiedDependencyException(
      @Nullable String resourceDescription, @Nullable String beanName, @Nullable InjectionPoint injectionPoint, BeansException ex
   ) {
      this(resourceDescription, beanName, injectionPoint, ex.getMessage());
      this.initCause(ex);
   }

   public @Nullable InjectionPoint getInjectionPoint() {
      return this.injectionPoint;
   }
}
