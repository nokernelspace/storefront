package org.springframework.beans.factory;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.FatalBeanException;

public class BeanDefinitionStoreException extends FatalBeanException {
   private final @Nullable String resourceDescription;
   private final @Nullable String beanName;

   public BeanDefinitionStoreException(String msg) {
      super(msg);
      this.resourceDescription = null;
      this.beanName = null;
   }

   public BeanDefinitionStoreException(String msg, @Nullable Throwable cause) {
      super(msg, cause);
      this.resourceDescription = null;
      this.beanName = null;
   }

   public BeanDefinitionStoreException(@Nullable String resourceDescription, String msg) {
      super(msg);
      this.resourceDescription = resourceDescription;
      this.beanName = null;
   }

   public BeanDefinitionStoreException(@Nullable String resourceDescription, String msg, @Nullable Throwable cause) {
      super(msg, cause);
      this.resourceDescription = resourceDescription;
      this.beanName = null;
   }

   public BeanDefinitionStoreException(@Nullable String resourceDescription, String beanName, String msg) {
      this(resourceDescription, beanName, msg, null);
   }

   public BeanDefinitionStoreException(@Nullable String resourceDescription, String beanName, @Nullable String msg, @Nullable Throwable cause) {
      super(
         msg == null
            ? "Invalid bean definition with name '" + beanName + "' defined in " + resourceDescription
            : "Invalid bean definition with name '" + beanName + "' defined in " + resourceDescription + ": " + msg,
         cause
      );
      this.resourceDescription = resourceDescription;
      this.beanName = beanName;
   }

   public @Nullable String getResourceDescription() {
      return this.resourceDescription;
   }

   public @Nullable String getBeanName() {
      return this.beanName;
   }
}
