package org.springframework.beans.factory.support;

import java.util.Properties;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanMetadataElement;
import org.springframework.beans.Mergeable;

public class ManagedProperties extends Properties implements Mergeable, BeanMetadataElement {
   private @Nullable Object source;
   private boolean mergeEnabled;

   public void setSource(@Nullable Object source) {
      this.source = source;
   }

   @Override
   public @Nullable Object getSource() {
      return this.source;
   }

   public void setMergeEnabled(boolean mergeEnabled) {
      this.mergeEnabled = mergeEnabled;
   }

   @Override
   public boolean isMergeEnabled() {
      return this.mergeEnabled;
   }

   @Override
   public Object merge(@Nullable Object parent) {
      if (!this.mergeEnabled) {
         throw new IllegalStateException("Not allowed to merge when the 'mergeEnabled' property is set to 'false'");
      } else if (parent == null) {
         return this;
      } else if (parent instanceof Properties properties) {
         Properties merged = new ManagedProperties();
         merged.putAll(properties);
         merged.putAll(this);
         return merged;
      } else {
         throw new IllegalArgumentException("Cannot merge with object of type [" + parent.getClass() + "]");
      }
   }
}
