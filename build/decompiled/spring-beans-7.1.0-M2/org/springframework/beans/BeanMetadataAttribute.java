package org.springframework.beans;

import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

public class BeanMetadataAttribute implements BeanMetadataElement {
   private final String name;
   private final @Nullable Object value;
   private @Nullable Object source;

   public BeanMetadataAttribute(String name, @Nullable Object value) {
      Assert.notNull(name, "Name must not be null");
      this.name = name;
      this.value = value;
   }

   public String getName() {
      return this.name;
   }

   public @Nullable Object getValue() {
      return this.value;
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
         || other instanceof BeanMetadataAttribute that
            && this.name.equals(that.name)
            && ObjectUtils.nullSafeEquals(this.value, that.value)
            && ObjectUtils.nullSafeEquals(this.source, that.source);
   }

   @Override
   public int hashCode() {
      return ObjectUtils.nullSafeHash(new Object[]{this.name, this.value});
   }

   @Override
   public String toString() {
      return "metadata attribute: name='" + this.name + "'; value=" + this.value;
   }
}
