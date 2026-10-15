package org.springframework.beans;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public abstract class AbstractPropertyAccessor extends TypeConverterSupport implements ConfigurablePropertyAccessor {
   private boolean extractOldValueForEditor = false;
   private boolean autoGrowNestedPaths = false;
   private int autoGrowCollectionLimit = Integer.MAX_VALUE;
   private int maxNestedPathDepth = 100;
   boolean suppressNotWritablePropertyException = false;

   @Override
   public void setExtractOldValueForEditor(boolean extractOldValueForEditor) {
      this.extractOldValueForEditor = extractOldValueForEditor;
   }

   @Override
   public boolean isExtractOldValueForEditor() {
      return this.extractOldValueForEditor;
   }

   @Override
   public void setAutoGrowNestedPaths(boolean autoGrowNestedPaths) {
      this.autoGrowNestedPaths = autoGrowNestedPaths;
   }

   @Override
   public boolean isAutoGrowNestedPaths() {
      return this.autoGrowNestedPaths;
   }

   @Override
   public void setAutoGrowCollectionLimit(int autoGrowCollectionLimit) {
      this.autoGrowCollectionLimit = autoGrowCollectionLimit;
   }

   @Override
   public int getAutoGrowCollectionLimit() {
      return this.autoGrowCollectionLimit;
   }

   @Override
   public void setMaxNestedPathDepth(int maxNestedPathDepth) {
      Assert.isTrue(maxNestedPathDepth >= 0, "'maxNestedPathDepth' must not be negative");
      this.maxNestedPathDepth = maxNestedPathDepth;
   }

   @Override
   public int getMaxNestedPathDepth() {
      return this.maxNestedPathDepth;
   }

   @Override
   public void setPropertyValue(PropertyValue pv) throws BeansException {
      this.setPropertyValue(pv.getName(), pv.getValue());
   }

   @Override
   public void setPropertyValues(Map<?, ?> map) throws BeansException {
      this.setPropertyValues(new MutablePropertyValues(map));
   }

   @Override
   public void setPropertyValues(PropertyValues pvs) throws BeansException {
      this.setPropertyValues(pvs, false, false);
   }

   @Override
   public void setPropertyValues(PropertyValues pvs, boolean ignoreUnknown) throws BeansException {
      this.setPropertyValues(pvs, ignoreUnknown, false);
   }

   @Override
   public void setPropertyValues(PropertyValues pvs, boolean ignoreUnknown, boolean ignoreInvalid) throws BeansException {
      List<PropertyAccessException> propertyAccessExceptions = null;
      List<PropertyValue> propertyValues = pvs instanceof MutablePropertyValues mpvs ? mpvs.getPropertyValueList() : Arrays.asList(pvs.getPropertyValues());
      if (ignoreUnknown) {
         this.suppressNotWritablePropertyException = true;
      }

      try {
         for (PropertyValue pv : propertyValues) {
            try {
               this.setPropertyValue(pv);
            } catch (NotWritablePropertyException ex) {
               if (!ignoreUnknown) {
                  throw ex;
               }
            } catch (NullValueInNestedPathException ex) {
               if (!ignoreInvalid) {
                  throw ex;
               }
            } catch (PropertyAccessException ex) {
               if (propertyAccessExceptions == null) {
                  propertyAccessExceptions = new ArrayList<>();
               }

               propertyAccessExceptions.add(ex);
            }
         }
      } finally {
         if (ignoreUnknown) {
            this.suppressNotWritablePropertyException = false;
         }
      }

      if (propertyAccessExceptions != null) {
         PropertyAccessException[] paeArray = propertyAccessExceptions.toArray(new PropertyAccessException[0]);
         throw new PropertyBatchUpdateException(paeArray);
      }
   }

   @Override
   public @Nullable Class<?> getPropertyType(String propertyPath) {
      return null;
   }

   @Override
   public abstract @Nullable Object getPropertyValue(String propertyName) throws BeansException;

   @Override
   public abstract void setPropertyValue(String propertyName, @Nullable Object value) throws BeansException;
}
