package org.springframework.validation;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.ConfigurablePropertyAccessor;
import org.springframework.beans.PropertyAccessorFactory;
import org.springframework.util.Assert;

public class DirectFieldBindingResult extends AbstractPropertyBindingResult {
   private final @Nullable Object target;
   private final boolean autoGrowNestedPaths;
   private final int autoGrowCollectionLimit;
   private final int maxNestedPathDepth;
   private transient @Nullable ConfigurablePropertyAccessor directFieldAccessor;

   public DirectFieldBindingResult(@Nullable Object target, String objectName) {
      this(target, objectName, true);
   }

   public DirectFieldBindingResult(@Nullable Object target, String objectName, boolean autoGrowNestedPaths) {
      this(target, objectName, autoGrowNestedPaths, Integer.MAX_VALUE, 100);
   }

   public DirectFieldBindingResult(@Nullable Object target, String objectName, boolean autoGrowNestedPaths, int autoGrowCollectionLimit, int maxNestedPathDepth) {
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
      if (this.directFieldAccessor == null) {
         this.directFieldAccessor = this.createDirectFieldAccessor();
         this.directFieldAccessor.setExtractOldValueForEditor(true);
         this.directFieldAccessor.setAutoGrowNestedPaths(this.autoGrowNestedPaths);
         this.directFieldAccessor.setAutoGrowCollectionLimit(this.autoGrowCollectionLimit);
         this.directFieldAccessor.setMaxNestedPathDepth(this.maxNestedPathDepth);
      }

      return this.directFieldAccessor;
   }

   protected ConfigurablePropertyAccessor createDirectFieldAccessor() {
      if (this.target == null) {
         throw new IllegalStateException("Cannot access fields on null target instance '" + this.getObjectName() + "'");
      } else {
         return PropertyAccessorFactory.forDirectFieldAccess(this.target);
      }
   }
}
