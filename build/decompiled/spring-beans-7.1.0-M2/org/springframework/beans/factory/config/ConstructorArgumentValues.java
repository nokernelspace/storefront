package org.springframework.beans.factory.config;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanMetadataElement;
import org.springframework.beans.Mergeable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;

public class ConstructorArgumentValues {
   private final Map<Integer, ConstructorArgumentValues.ValueHolder> indexedArgumentValues = new LinkedHashMap<>();
   private final List<ConstructorArgumentValues.ValueHolder> genericArgumentValues = new ArrayList<>();

   public ConstructorArgumentValues() {
   }

   public ConstructorArgumentValues(ConstructorArgumentValues original) {
      this.addArgumentValues(original);
   }

   public void addArgumentValues(@Nullable ConstructorArgumentValues other) {
      if (other != null) {
         other.indexedArgumentValues.forEach((index, argValue) -> this.addOrMergeIndexedArgumentValue(index, argValue.copy()));
         other.genericArgumentValues
            .stream()
            .filter(valueHolder -> !this.genericArgumentValues.contains(valueHolder))
            .forEach(valueHolder -> this.addOrMergeGenericArgumentValue(valueHolder.copy()));
      }
   }

   public void addIndexedArgumentValue(int index, @Nullable Object value) {
      this.addIndexedArgumentValue(index, new ConstructorArgumentValues.ValueHolder(value));
   }

   public void addIndexedArgumentValue(int index, @Nullable Object value, String type) {
      this.addIndexedArgumentValue(index, new ConstructorArgumentValues.ValueHolder(value, type));
   }

   public void addIndexedArgumentValue(int index, ConstructorArgumentValues.ValueHolder newValue) {
      Assert.isTrue(index >= 0, "Index must not be negative");
      Assert.notNull(newValue, "ValueHolder must not be null");
      this.addOrMergeIndexedArgumentValue(index, newValue);
   }

   private void addOrMergeIndexedArgumentValue(Integer key, ConstructorArgumentValues.ValueHolder newValue) {
      ConstructorArgumentValues.ValueHolder currentValue = this.indexedArgumentValues.get(key);
      if (currentValue != null && newValue.getValue() instanceof Mergeable mergeable && mergeable.isMergeEnabled()) {
         newValue.setValue(mergeable.merge(currentValue.getValue()));
      }

      this.indexedArgumentValues.put(key, newValue);
   }

   public boolean hasIndexedArgumentValue(int index) {
      return this.indexedArgumentValues.containsKey(index);
   }

   public ConstructorArgumentValues.@Nullable ValueHolder getIndexedArgumentValue(int index, @Nullable Class<?> requiredType) {
      return this.getIndexedArgumentValue(index, requiredType, null);
   }

   public ConstructorArgumentValues.@Nullable ValueHolder getIndexedArgumentValue(int index, @Nullable Class<?> requiredType, @Nullable String requiredName) {
      Assert.isTrue(index >= 0, "Index must not be negative");
      ConstructorArgumentValues.ValueHolder valueHolder = this.indexedArgumentValues.get(index);
      return valueHolder == null
            || valueHolder.getType() != null && (requiredType == null || !ClassUtils.matchesTypeName(requiredType, valueHolder.getType()))
            || valueHolder.getName() != null && (requiredName == null || !requiredName.isEmpty() && !requiredName.equals(valueHolder.getName()))
         ? null
         : valueHolder;
   }

   public Map<Integer, ConstructorArgumentValues.ValueHolder> getIndexedArgumentValues() {
      return Collections.unmodifiableMap(this.indexedArgumentValues);
   }

   public void addGenericArgumentValue(@Nullable Object value) {
      this.genericArgumentValues.add(new ConstructorArgumentValues.ValueHolder(value));
   }

   public void addGenericArgumentValue(Object value, String type) {
      this.genericArgumentValues.add(new ConstructorArgumentValues.ValueHolder(value, type));
   }

   public void addGenericArgumentValue(ConstructorArgumentValues.ValueHolder newValue) {
      Assert.notNull(newValue, "ValueHolder must not be null");
      if (!this.genericArgumentValues.contains(newValue)) {
         this.addOrMergeGenericArgumentValue(newValue);
      }
   }

   private void addOrMergeGenericArgumentValue(ConstructorArgumentValues.ValueHolder newValue) {
      if (newValue.getName() != null) {
         Iterator<ConstructorArgumentValues.ValueHolder> it = this.genericArgumentValues.iterator();

         while (it.hasNext()) {
            ConstructorArgumentValues.ValueHolder currentValue = it.next();
            if (newValue.getName().equals(currentValue.getName())) {
               if (newValue.getValue() instanceof Mergeable mergeable && mergeable.isMergeEnabled()) {
                  newValue.setValue(mergeable.merge(currentValue.getValue()));
               }

               it.remove();
            }
         }
      }

      this.genericArgumentValues.add(newValue);
   }

   public ConstructorArgumentValues.@Nullable ValueHolder getGenericArgumentValue(Class<?> requiredType) {
      return this.getGenericArgumentValue(requiredType, null, null);
   }

   public ConstructorArgumentValues.@Nullable ValueHolder getGenericArgumentValue(Class<?> requiredType, String requiredName) {
      return this.getGenericArgumentValue(requiredType, requiredName, null);
   }

   public ConstructorArgumentValues.@Nullable ValueHolder getGenericArgumentValue(
      @Nullable Class<?> requiredType, @Nullable String requiredName, @Nullable Set<ConstructorArgumentValues.ValueHolder> usedValueHolders
   ) {
      for (ConstructorArgumentValues.ValueHolder valueHolder : this.genericArgumentValues) {
         if ((usedValueHolders == null || !usedValueHolders.contains(valueHolder))
            && (valueHolder.getName() == null || requiredName != null && (requiredName.isEmpty() || requiredName.equals(valueHolder.getName())))
            && (valueHolder.getType() == null || requiredType != null && ClassUtils.matchesTypeName(requiredType, valueHolder.getType()))
            && (
               requiredType == null
                  || valueHolder.getType() != null
                  || valueHolder.getName() != null
                  || ClassUtils.isAssignableValue(requiredType, valueHolder.getValue())
            )) {
            return valueHolder;
         }
      }

      return null;
   }

   public List<ConstructorArgumentValues.ValueHolder> getGenericArgumentValues() {
      return Collections.unmodifiableList(this.genericArgumentValues);
   }

   public ConstructorArgumentValues.@Nullable ValueHolder getArgumentValue(int index, Class<?> requiredType) {
      return this.getArgumentValue(index, requiredType, null, null);
   }

   public ConstructorArgumentValues.@Nullable ValueHolder getArgumentValue(int index, Class<?> requiredType, String requiredName) {
      return this.getArgumentValue(index, requiredType, requiredName, null);
   }

   public ConstructorArgumentValues.@Nullable ValueHolder getArgumentValue(
      int index, @Nullable Class<?> requiredType, @Nullable String requiredName, @Nullable Set<ConstructorArgumentValues.ValueHolder> usedValueHolders
   ) {
      Assert.isTrue(index >= 0, "Index must not be negative");
      ConstructorArgumentValues.ValueHolder valueHolder = this.getIndexedArgumentValue(index, requiredType, requiredName);
      if (valueHolder == null) {
         valueHolder = this.getGenericArgumentValue(requiredType, requiredName, usedValueHolders);
      }

      return valueHolder;
   }

   public boolean containsNamedArgument() {
      for (ConstructorArgumentValues.ValueHolder valueHolder : this.indexedArgumentValues.values()) {
         if (valueHolder.getName() != null) {
            return true;
         }
      }

      for (ConstructorArgumentValues.ValueHolder valueHolder : this.genericArgumentValues) {
         if (valueHolder.getName() != null) {
            return true;
         }
      }

      return false;
   }

   public int getArgumentCount() {
      return this.indexedArgumentValues.size() + this.genericArgumentValues.size();
   }

   public boolean isEmpty() {
      return this.indexedArgumentValues.isEmpty() && this.genericArgumentValues.isEmpty();
   }

   public void clear() {
      this.indexedArgumentValues.clear();
      this.genericArgumentValues.clear();
   }

   @Override
   public boolean equals(@Nullable Object other) {
      if (this == other) {
         return true;
      } else if (!(other instanceof ConstructorArgumentValues that)) {
         return false;
      } else if (this.genericArgumentValues.size() == that.genericArgumentValues.size()
         && this.indexedArgumentValues.size() == that.indexedArgumentValues.size()) {
         Iterator<ConstructorArgumentValues.ValueHolder> it1 = this.genericArgumentValues.iterator();
         Iterator<ConstructorArgumentValues.ValueHolder> it2 = that.genericArgumentValues.iterator();

         while (it1.hasNext() && it2.hasNext()) {
            ConstructorArgumentValues.ValueHolder vh1 = it1.next();
            ConstructorArgumentValues.ValueHolder vh2 = it2.next();
            if (!vh1.contentEquals(vh2)) {
               return false;
            }
         }

         for (Entry<Integer, ConstructorArgumentValues.ValueHolder> entry : this.indexedArgumentValues.entrySet()) {
            ConstructorArgumentValues.ValueHolder vh1 = entry.getValue();
            ConstructorArgumentValues.ValueHolder vh2 = that.indexedArgumentValues.get(entry.getKey());
            if (vh2 == null || !vh1.contentEquals(vh2)) {
               return false;
            }
         }

         return true;
      } else {
         return false;
      }
   }

   @Override
   public int hashCode() {
      int hashCode = 7;

      for (ConstructorArgumentValues.ValueHolder valueHolder : this.genericArgumentValues) {
         hashCode = 31 * hashCode + valueHolder.contentHashCode();
      }

      hashCode = 29 * hashCode;

      for (Entry<Integer, ConstructorArgumentValues.ValueHolder> entry : this.indexedArgumentValues.entrySet()) {
         hashCode = 31 * hashCode + (entry.getValue().contentHashCode() ^ entry.getKey().hashCode());
      }

      return hashCode;
   }

   public static class ValueHolder implements BeanMetadataElement {
      private @Nullable Object value;
      private @Nullable String type;
      private @Nullable String name;
      private @Nullable Object source;
      private boolean converted = false;
      private @Nullable Object convertedValue;

      public ValueHolder(@Nullable Object value) {
         this.value = value;
      }

      public ValueHolder(@Nullable Object value, @Nullable String type) {
         this.value = value;
         this.type = type;
      }

      public ValueHolder(@Nullable Object value, @Nullable String type, @Nullable String name) {
         this.value = value;
         this.type = type;
         this.name = name;
      }

      public void setValue(@Nullable Object value) {
         this.value = value;
      }

      public @Nullable Object getValue() {
         return this.value;
      }

      public void setType(@Nullable String type) {
         this.type = type;
      }

      public @Nullable String getType() {
         return this.type;
      }

      public void setName(@Nullable String name) {
         this.name = name;
      }

      public @Nullable String getName() {
         return this.name;
      }

      public void setSource(@Nullable Object source) {
         this.source = source;
      }

      @Override
      public @Nullable Object getSource() {
         return this.source;
      }

      public synchronized boolean isConverted() {
         return this.converted;
      }

      public synchronized void setConvertedValue(@Nullable Object value) {
         this.converted = value != null;
         this.convertedValue = value;
      }

      public synchronized @Nullable Object getConvertedValue() {
         return this.convertedValue;
      }

      private boolean contentEquals(ConstructorArgumentValues.ValueHolder other) {
         return this == other || ObjectUtils.nullSafeEquals(this.value, other.value) && ObjectUtils.nullSafeEquals(this.type, other.type);
      }

      private int contentHashCode() {
         return ObjectUtils.nullSafeHash(new Object[]{this.value, this.type});
      }

      public ConstructorArgumentValues.ValueHolder copy() {
         ConstructorArgumentValues.ValueHolder copy = new ConstructorArgumentValues.ValueHolder(this.value, this.type, this.name);
         copy.setSource(this.source);
         return copy;
      }
   }
}
