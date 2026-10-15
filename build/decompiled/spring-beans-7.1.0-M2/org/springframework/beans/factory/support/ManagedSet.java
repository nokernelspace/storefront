package org.springframework.beans.factory.support;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanMetadataElement;
import org.springframework.beans.Mergeable;

public class ManagedSet<E> extends LinkedHashSet<E> implements Mergeable, BeanMetadataElement {
   private @Nullable Object source;
   private @Nullable String elementTypeName;
   private boolean mergeEnabled;

   public ManagedSet() {
   }

   public ManagedSet(int initialCapacity) {
      super(initialCapacity);
   }

   @SafeVarargs
   public static <E> ManagedSet<E> of(E... elements) {
      ManagedSet<E> set = new ManagedSet<>();
      Collections.addAll(set, elements);
      return set;
   }

   public void setSource(@Nullable Object source) {
      this.source = source;
   }

   @Override
   public @Nullable Object getSource() {
      return this.source;
   }

   public void setElementTypeName(@Nullable String elementTypeName) {
      this.elementTypeName = elementTypeName;
   }

   public @Nullable String getElementTypeName() {
      return this.elementTypeName;
   }

   public void setMergeEnabled(boolean mergeEnabled) {
      this.mergeEnabled = mergeEnabled;
   }

   @Override
   public boolean isMergeEnabled() {
      return this.mergeEnabled;
   }

   public Set<E> merge(@Nullable Object parent) {
      if (!this.mergeEnabled) {
         throw new IllegalStateException("Not allowed to merge when the 'mergeEnabled' property is set to 'false'");
      }

      if (parent == null) {
         return this;
      }

      if (!(parent instanceof Set)) {
         throw new IllegalArgumentException("Cannot merge with object of type [" + parent.getClass() + "]");
      }

      Set<E> merged = new ManagedSet<>();
      merged.addAll((Set)parent);
      merged.addAll(this);
      return merged;
   }
}
