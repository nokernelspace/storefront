package org.springframework.beans.factory.config;

import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public class RuntimeBeanNameReference implements BeanReference {
   private final String beanName;
   private @Nullable Object source;

   public RuntimeBeanNameReference(String beanName) {
      Assert.hasText(beanName, "'beanName' must not be empty");
      this.beanName = beanName;
   }

   @Override
   public String getBeanName() {
      return this.beanName;
   }

   public void setSource(@Nullable Object source) {
      this.source = source;
   }

   @Override
   public @Nullable Object getSource() {
      return this.source;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof RuntimeBeanNameReference that && this.beanName.equals(that.beanName);
   }

   @Override
   public int hashCode() {
      return this.beanName.hashCode();
   }

   @Override
   public String toString() {
      return "<" + this.getBeanName() + ">";
   }
}
