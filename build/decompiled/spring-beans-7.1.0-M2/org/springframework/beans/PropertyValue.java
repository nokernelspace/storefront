package org.springframework.beans;

import java.io.Serializable;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

public class PropertyValue extends BeanMetadataAttributeAccessor implements Serializable {
   private final String name;
   private final @Nullable Object value;
   private boolean optional = false;
   private boolean converted = false;
   private @Nullable Object convertedValue;
   volatile @Nullable Boolean conversionNecessary;
   transient volatile @Nullable Object resolvedTokens;

   public PropertyValue(String name, @Nullable Object value) {
      Assert.notNull(name, "Name must not be null");
      this.name = name;
      this.value = value;
   }

   public PropertyValue(PropertyValue original) {
      Assert.notNull(original, "Original must not be null");
      this.name = original.getName();
      this.value = original.getValue();
      this.optional = original.isOptional();
      this.converted = original.converted;
      this.convertedValue = original.convertedValue;
      this.conversionNecessary = original.conversionNecessary;
      this.resolvedTokens = original.resolvedTokens;
      this.setSource(original.getSource());
      this.copyAttributesFrom(original);
   }

   public PropertyValue(PropertyValue original, @Nullable Object newValue) {
      Assert.notNull(original, "Original must not be null");
      this.name = original.getName();
      this.value = newValue;
      this.optional = original.isOptional();
      this.conversionNecessary = original.conversionNecessary;
      this.resolvedTokens = original.resolvedTokens;
      this.setSource(original);
      this.copyAttributesFrom(original);
   }

   public String getName() {
      return this.name;
   }

   public @Nullable Object getValue() {
      return this.value;
   }

   public PropertyValue getOriginalPropertyValue() {
      PropertyValue original = this;

      for (Object source = this.getSource(); source instanceof PropertyValue; source = original.getSource()) {
         PropertyValue pv = (PropertyValue)source;
         if (source == original) {
            break;
         }

         original = pv;
      }

      return original;
   }

   public void setOptional(boolean optional) {
      this.optional = optional;
   }

   public boolean isOptional() {
      return this.optional;
   }

   public synchronized boolean isConverted() {
      return this.converted;
   }

   public synchronized void setConvertedValue(@Nullable Object value) {
      this.converted = true;
      this.convertedValue = value;
   }

   public synchronized @Nullable Object getConvertedValue() {
      return this.convertedValue;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof PropertyValue that
            && this.name.equals(that.name)
            && ObjectUtils.nullSafeEquals(this.value, that.value)
            && ObjectUtils.nullSafeEquals(this.getSource(), that.getSource());
   }

   @Override
   public int hashCode() {
      return ObjectUtils.nullSafeHash(new Object[]{this.name, this.value});
   }

   @Override
   public String toString() {
      return "bean property '" + this.name + "'";
   }
}
