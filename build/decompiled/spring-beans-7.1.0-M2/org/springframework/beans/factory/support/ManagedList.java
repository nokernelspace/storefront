package org.springframework.beans.factory.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanMetadataElement;
import org.springframework.beans.Mergeable;

public class ManagedList<E> extends ArrayList<E> implements Mergeable, BeanMetadataElement {
   private @Nullable Object source;
   private @Nullable String elementTypeName;
   private boolean mergeEnabled;

   public ManagedList() {
   }

   public ManagedList(int initialCapacity) {
      super(initialCapacity);
   }

   @SafeVarargs
   public static <E> ManagedList<E> of(E... elements) {
      ManagedList<E> list = new ManagedList<>();
      Collections.addAll(list, elements);
      return list;
   }

   public void setSource(@Nullable Object source) {
      this.source = source;
   }

   @Override
   public @Nullable Object getSource() {
      return this.source;
   }

   public void setElementTypeName(String elementTypeName) {
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

   public List<E> merge(@Nullable Object parent) {
      if (!this.mergeEnabled) {
         throw new IllegalStateException("Not allowed to merge when the 'mergeEnabled' property is set to 'false'");
      }

      if (parent == null) {
         return this;
      }

      if (!(parent instanceof List)) {
         throw new IllegalArgumentException("Cannot merge with object of type [" + parent.getClass() + "]");
      }

      List<E> merged = new ManagedList<>();
      merged.addAll((List)parent);
      merged.addAll(this);
      return merged;
   }
}
