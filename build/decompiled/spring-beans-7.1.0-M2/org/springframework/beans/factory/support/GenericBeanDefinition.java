package org.springframework.beans.factory.support;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.util.ObjectUtils;

public class GenericBeanDefinition extends AbstractBeanDefinition {
   private @Nullable String parentName;

   public GenericBeanDefinition() {
   }

   public GenericBeanDefinition(BeanDefinition original) {
      super(original);
   }

   @Override
   public void setParentName(@Nullable String parentName) {
      this.parentName = parentName;
   }

   @Override
   public @Nullable String getParentName() {
      return this.parentName;
   }

   @Override
   public AbstractBeanDefinition cloneBeanDefinition() {
      return new GenericBeanDefinition(this);
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof GenericBeanDefinition that && ObjectUtils.nullSafeEquals(this.parentName, that.parentName) && super.equals(other);
   }

   @Override
   public String toString() {
      return this.parentName != null ? "Generic bean with parent '" + this.parentName + "': " + super.toString() : "Generic bean: " + super.toString();
   }
}
