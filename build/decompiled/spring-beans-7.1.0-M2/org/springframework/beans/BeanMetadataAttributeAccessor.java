package org.springframework.beans;

import org.jspecify.annotations.Nullable;
import org.springframework.core.AttributeAccessorSupport;

public class BeanMetadataAttributeAccessor extends AttributeAccessorSupport implements BeanMetadataElement {
   private @Nullable Object source;

   public void setSource(@Nullable Object source) {
      this.source = source;
   }

   @Override
   public @Nullable Object getSource() {
      return this.source;
   }

   public void addMetadataAttribute(BeanMetadataAttribute attribute) {
      super.setAttribute(attribute.getName(), attribute);
   }

   public @Nullable BeanMetadataAttribute getMetadataAttribute(String name) {
      return (BeanMetadataAttribute)super.getAttribute(name);
   }

   public void setAttribute(String name, @Nullable Object value) {
      super.setAttribute(name, new BeanMetadataAttribute(name, value));
   }

   public @Nullable Object getAttribute(String name) {
      BeanMetadataAttribute attribute = (BeanMetadataAttribute)super.getAttribute(name);
      return attribute != null ? attribute.getValue() : null;
   }

   public @Nullable Object removeAttribute(String name) {
      BeanMetadataAttribute attribute = (BeanMetadataAttribute)super.removeAttribute(name);
      return attribute != null ? attribute.getValue() : null;
   }
}
