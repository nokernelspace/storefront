package org.springframework.validation;

import java.io.Serializable;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.ConfigurablePropertyAccessor;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.util.Assert;

public class BeanPropertyBindingResult extends AbstractPropertyBindingResult implements Serializable {
   private final @Nullable Object target;
   private final boolean autoGrowNestedPaths;
   private final int autoGrowCollectionLimit;
   private final int maxNestedPathDepth;
   private transient @Nullable BeanWrapper beanWrapper;

   public BeanPropertyBindingResult(@Nullable Object target, String objectName) {
      this(target, objectName, true, Integer.MAX_VALUE);
   }

   public BeanPropertyBindingResult(@Nullable Object target, String objectName, boolean autoGrowNestedPaths, int autoGrowCollectionLimit) {
      this(target, objectName, autoGrowNestedPaths, autoGrowCollectionLimit, 100);
   }

   public BeanPropertyBindingResult(
      @Nullable Object target, String objectName, boolean autoGrowNestedPaths, int autoGrowCollectionLimit, int maxNestedPathDepth
   ) {
      super(objectName);
      Assert.isTrue(maxNestedPathDepth >= 0, "'maxNestedPathDepth' must not be negative");
      this.target = target;
      this.autoGrowNestedPaths = autoGrowNestedPaths;
      this.autoGrowCollectionLimit = autoGrowCollectionLimit;
      this.maxNestedPathDepth = maxNestedPathDepth;
   }

   @Override
   public final @Nullable Object getTarget() {
      return this.target;
   }

   @Override
   public final ConfigurablePropertyAccessor getPropertyAccessor() {
      if (this.beanWrapper == null) {
         this.beanWrapper = this.createBeanWrapper();
         this.beanWrapper.setExtractOldValueForEditor(true);
         this.beanWrapper.setAutoGrowNestedPaths(this.autoGrowNestedPaths);
         this.beanWrapper.setAutoGrowCollectionLimit(this.autoGrowCollectionLimit);
         this.beanWrapper.setMaxNestedPathDepth(this.maxNestedPathDepth);
      }

      return this.beanWrapper;
   }

   protected BeanWrapper createBeanWrapper() {
      if (this.target == null) {
         throw new IllegalStateException("Cannot access properties on null bean instance '" + this.getObjectName() + "'");
      } else {
         return PropertyAccessorFactory.forBeanPropertyAccess(this.target);
      }
   }
}
