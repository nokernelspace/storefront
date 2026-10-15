package org.springframework.beans;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Spliterator;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

public class MutablePropertyValues implements PropertyValues, Serializable {
   private static final PropertyValue[] EMPTY_PROPERTY_VALUE_ARRAY = new PropertyValue[0];
   private final List<PropertyValue> propertyValueList;
   private @Nullable Set<String> processedProperties;
   private volatile boolean converted;

   public MutablePropertyValues() {
      this.propertyValueList = new ArrayList<>(0);
   }

   public MutablePropertyValues(@Nullable PropertyValues original) {
      if (original != null) {
         PropertyValue[] pvs = original.getPropertyValues();
         this.propertyValueList = new ArrayList<>(pvs.length);

         for (PropertyValue pv : pvs) {
            this.propertyValueList.add(new PropertyValue(pv));
         }
      } else {
         this.propertyValueList = new ArrayList<>(0);
      }
   }

   public MutablePropertyValues(@Nullable Map<?, ?> original) {
      if (original != null) {
         this.propertyValueList = new ArrayList<>(original.size());
         original.forEach((attrName, attrValue) -> this.propertyValueList.add(new PropertyValue(attrName.toString(), attrValue)));
      } else {
         this.propertyValueList = new ArrayList<>(0);
      }
   }

   public MutablePropertyValues(@Nullable List<PropertyValue> propertyValueList) {
      this.propertyValueList = propertyValueList != null ? propertyValueList : new ArrayList<>();
   }

   public List<PropertyValue> getPropertyValueList() {
      return this.propertyValueList;
   }

   public int size() {
      return this.propertyValueList.size();
   }

   public MutablePropertyValues addPropertyValues(@Nullable PropertyValues other) {
      if (other != null) {
         PropertyValue[] pvs = other.getPropertyValues();

         for (PropertyValue pv : pvs) {
            this.addPropertyValue(new PropertyValue(pv));
         }
      }

      return this;
   }

   public MutablePropertyValues addPropertyValues(@Nullable Map<?, ?> other) {
      if (other != null) {
         other.forEach((attrName, attrValue) -> this.addPropertyValue(new PropertyValue(attrName.toString(), attrValue)));
      }

      return this;
   }

   public MutablePropertyValues addPropertyValue(PropertyValue pv) {
      for (int i = 0; i < this.propertyValueList.size(); i++) {
         PropertyValue currentPv = this.propertyValueList.get(i);
         if (currentPv.getName().equals(pv.getName())) {
            pv = this.mergeIfRequired(pv, currentPv);
            this.setPropertyValueAt(pv, i);
            return this;
         }
      }

      this.propertyValueList.add(pv);
      return this;
   }

   public void addPropertyValue(String propertyName, Object propertyValue) {
      this.addPropertyValue(new PropertyValue(propertyName, propertyValue));
   }

   public MutablePropertyValues add(String propertyName, @Nullable Object propertyValue) {
      this.addPropertyValue(new PropertyValue(propertyName, propertyValue));
      return this;
   }

   public void setPropertyValueAt(PropertyValue pv, int i) {
      this.propertyValueList.set(i, pv);
   }

   private PropertyValue mergeIfRequired(PropertyValue newPv, PropertyValue currentPv) {
      if (newPv.getValue() instanceof Mergeable mergeable && mergeable.isMergeEnabled()) {
         Object merged = mergeable.merge(currentPv.getValue());
         return new PropertyValue(newPv.getName(), merged);
      } else {
         return newPv;
      }
   }

   public void removePropertyValue(PropertyValue pv) {
      this.propertyValueList.remove(pv);
   }

   public void removePropertyValue(String propertyName) {
      this.propertyValueList.remove(this.getPropertyValue(propertyName));
   }

   @Override
   public Iterator<PropertyValue> iterator() {
      return Collections.unmodifiableList(this.propertyValueList).iterator();
   }

   @Override
   public Spliterator<PropertyValue> spliterator() {
      return this.propertyValueList.spliterator();
   }

   @Override
   public Stream<PropertyValue> stream() {
      return this.propertyValueList.stream();
   }

   @Override
   public PropertyValue[] getPropertyValues() {
      return this.propertyValueList.toArray(EMPTY_PROPERTY_VALUE_ARRAY);
   }

   @Override
   public @Nullable PropertyValue getPropertyValue(String propertyName) {
      for (PropertyValue pv : this.propertyValueList) {
         if (pv.getName().equals(propertyName)) {
            return pv;
         }
      }

      return null;
   }

   public @Nullable Object get(String propertyName) {
      PropertyValue pv = this.getPropertyValue(propertyName);
      return pv != null ? pv.getValue() : null;
   }

   @Override
   public PropertyValues changesSince(PropertyValues old) {
      MutablePropertyValues changes = new MutablePropertyValues();
      if (old == this) {
         return changes;
      }

      for (PropertyValue newPv : this.propertyValueList) {
         PropertyValue pvOld = old.getPropertyValue(newPv.getName());
         if (pvOld == null || !pvOld.equals(newPv)) {
            changes.addPropertyValue(newPv);
         }
      }

      return changes;
   }

   @Override
   public boolean contains(String propertyName) {
      return this.getPropertyValue(propertyName) != null || this.processedProperties != null && this.processedProperties.contains(propertyName);
   }

   @Override
   public boolean isEmpty() {
      return this.propertyValueList.isEmpty();
   }

   public void registerProcessedProperty(String propertyName) {
      if (this.processedProperties == null) {
         this.processedProperties = new HashSet<>(4);
      }

      this.processedProperties.add(propertyName);
   }

   public void clearProcessedProperty(String propertyName) {
      if (this.processedProperties != null) {
         this.processedProperties.remove(propertyName);
      }
   }

   public void setConverted() {
      this.converted = true;
   }

   public boolean isConverted() {
      return this.converted;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof MutablePropertyValues that && this.propertyValueList.equals(that.propertyValueList);
   }

   @Override
   public int hashCode() {
      return this.propertyValueList.hashCode();
   }

   @Override
   public String toString() {
      PropertyValue[] pvs = this.getPropertyValues();
      return pvs.length > 0 ? "PropertyValues: length=" + pvs.length + "; " + StringUtils.arrayToDelimitedString(pvs, "; ") : "PropertyValues: length=0";
   }
}
