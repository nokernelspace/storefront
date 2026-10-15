package org.springframework.beans.factory.wiring;

import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public class BeanWiringInfo {
   public static final int AUTOWIRE_BY_NAME = 1;
   public static final int AUTOWIRE_BY_TYPE = 2;
   private @Nullable String beanName;
   private boolean isDefaultBeanName = false;
   private int autowireMode = 0;
   private boolean dependencyCheck = false;

   public BeanWiringInfo() {
   }

   public BeanWiringInfo(String beanName) {
      this(beanName, false);
   }

   public BeanWiringInfo(String beanName, boolean isDefaultBeanName) {
      Assert.hasText(beanName, "'beanName' must not be empty");
      this.beanName = beanName;
      this.isDefaultBeanName = isDefaultBeanName;
   }

   public BeanWiringInfo(int autowireMode, boolean dependencyCheck) {
      if (autowireMode != 1 && autowireMode != 2) {
         throw new IllegalArgumentException("Only constants AUTOWIRE_BY_NAME and AUTOWIRE_BY_TYPE supported");
      }

      this.autowireMode = autowireMode;
      this.dependencyCheck = dependencyCheck;
   }

   public boolean indicatesAutowiring() {
      return this.beanName == null;
   }

   public @Nullable String getBeanName() {
      return this.beanName;
   }

   public boolean isDefaultBeanName() {
      return this.isDefaultBeanName;
   }

   public int getAutowireMode() {
      return this.autowireMode;
   }

   public boolean getDependencyCheck() {
      return this.dependencyCheck;
   }
}
