package org.springframework.beans.factory.config;

import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public class RuntimeBeanReference implements BeanReference {
   private final String beanName;
   private final @Nullable Class<?> beanType;
   private final boolean toParent;
   private @Nullable Object source;

   public RuntimeBeanReference(String beanName) {
      this(beanName, false);
   }

   public RuntimeBeanReference(String beanName, boolean toParent) {
      Assert.hasText(beanName, "'beanName' must not be empty");
      this.beanName = beanName;
      this.beanType = null;
      this.toParent = toParent;
   }

   public RuntimeBeanReference(Class<?> beanType) {
      this(beanType, false);
   }

   public RuntimeBeanReference(Class<?> beanType, boolean toParent) {
      Assert.notNull(beanType, "'beanType' must not be null");
      this.beanName = beanType.getName();
      this.beanType = beanType;
      this.toParent = toParent;
   }

   public RuntimeBeanReference(String beanName, Class<?> beanType) {
      this(beanName, beanType, false);
   }

   public RuntimeBeanReference(String beanName, Class<?> beanType, boolean toParent) {
      Assert.hasText(beanName, "'beanName' must not be empty");
      Assert.notNull(beanType, "'beanType' must not be null");
      this.beanName = beanName;
      this.beanType = beanType;
      this.toParent = toParent;
   }

   @Override
   public String getBeanName() {
      return this.beanName;
   }

   public @Nullable Class<?> getBeanType() {
      return this.beanType;
   }

   public boolean isToParent() {
      return this.toParent;
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
      return this == other
         || other instanceof RuntimeBeanReference that
            && this.beanName.equals(that.beanName)
            && this.beanType == that.beanType
            && this.toParent == that.toParent;
   }

   @Override
   public int hashCode() {
      int result = this.beanName.hashCode();
      return 29 * result + (this.toParent ? 1 : 0);
   }

   @Override
   public String toString() {
      return "<" + this.getBeanName() + ">";
   }
}
