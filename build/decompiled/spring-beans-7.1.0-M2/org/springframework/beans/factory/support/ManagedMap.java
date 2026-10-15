package org.springframework.beans.factory.support;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanMetadataElement;
import org.springframework.beans.Mergeable;

public class ManagedMap<K, V> extends LinkedHashMap<K, V> implements Mergeable, BeanMetadataElement {
   private @Nullable Object source;
   private @Nullable String keyTypeName;
   private @Nullable String valueTypeName;
   private boolean mergeEnabled;

   public ManagedMap() {
   }

   public ManagedMap(int initialCapacity) {
      super(initialCapacity);
   }

   @SafeVarargs
   public static <K, V> ManagedMap<K, V> ofEntries(Entry<? extends K, ? extends V>... entries) {
      ManagedMap<K, V> map = new ManagedMap<>();

      for (Entry<? extends K, ? extends V> entry : entries) {
         map.put((K)entry.getKey(), (V)entry.getValue());
      }

      return map;
   }

   public void setSource(@Nullable Object source) {
      this.source = source;
   }

   @Override
   public @Nullable Object getSource() {
      return this.source;
   }

   public void setKeyTypeName(@Nullable String keyTypeName) {
      this.keyTypeName = keyTypeName;
   }

   public @Nullable String getKeyTypeName() {
      return this.keyTypeName;
   }

   public void setValueTypeName(@Nullable String valueTypeName) {
      this.valueTypeName = valueTypeName;
   }

   public @Nullable String getValueTypeName() {
      return this.valueTypeName;
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
      }

      if (parent == null) {
         return this;
      }

      if (!(parent instanceof Map)) {
         throw new IllegalArgumentException("Cannot merge with object of type [" + parent.getClass() + "]");
      }

      Map<K, V> merged = new ManagedMap<>();
      merged.putAll((Map<? extends K, ? extends V>)parent);
      merged.putAll(this);
      return merged;
   }
}
