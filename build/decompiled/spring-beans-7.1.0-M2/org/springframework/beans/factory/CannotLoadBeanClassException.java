package org.springframework.beans.factory;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.FatalBeanException;

public class CannotLoadBeanClassException extends FatalBeanException {
   private final @Nullable String resourceDescription;
   private final String beanName;
   private final @Nullable String beanClassName;

   public CannotLoadBeanClassException(@Nullable String resourceDescription, String beanName, @Nullable String beanClassName, ClassNotFoundException cause) {
      super(
         "Cannot find class ["
            + beanClassName
            + "] for bean with name '"
            + beanName
            + "'"
            + (resourceDescription != null ? " defined in " + resourceDescription : ""),
         cause
      );
      this.resourceDescription = resourceDescription;
      this.beanName = beanName;
      this.beanClassName = beanClassName;
   }

   public CannotLoadBeanClassException(@Nullable String resourceDescription, String beanName, @Nullable String beanClassName, LinkageError cause) {
      super(
         "Error loading class ["
            + beanClassName
            + "] for bean with name '"
            + beanName
            + "'"
            + (resourceDescription != null ? " defined in " + resourceDescription : "")
            + ": problem with class file or dependent class",
         cause
      );
      this.resourceDescription = resourceDescription;
      this.beanName = beanName;
      this.beanClassName = beanClassName;
   }

   public @Nullable String getResourceDescription() {
      return this.resourceDescription;
   }

   public String getBeanName() {
      return this.beanName;
   }

   public @Nullable String getBeanClassName() {
      return this.beanClassName;
   }
}
