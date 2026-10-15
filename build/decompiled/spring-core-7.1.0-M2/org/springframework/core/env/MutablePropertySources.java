package org.springframework.core.env;

import java.util.Iterator;
import java.util.List;
import java.util.Spliterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

public class MutablePropertySources implements PropertySources {
   private final List<PropertySource<?>> propertySourceList = new CopyOnWriteArrayList<>();

   public MutablePropertySources() {
   }

   public MutablePropertySources(PropertySources propertySources) {
      this();

      for (PropertySource<?> propertySource : propertySources) {
         this.addLast(propertySource);
      }
   }

   @Override
   public Iterator<PropertySource<?>> iterator() {
      return this.propertySourceList.iterator();
   }

   @Override
   public Spliterator<PropertySource<?>> spliterator() {
      return this.propertySourceList.spliterator();
   }

   @Override
   public Stream<PropertySource<?>> stream() {
      return this.propertySourceList.stream();
   }

   @Override
   public boolean contains(String name) {
      for (PropertySource<?> propertySource : this.propertySourceList) {
         if (propertySource.getName().equals(name)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public @Nullable PropertySource<?> get(String name) {
      for (PropertySource<?> propertySource : this.propertySourceList) {
         if (propertySource.getName().equals(name)) {
            return propertySource;
         }
      }

      return null;
   }

   public void addFirst(PropertySource<?> propertySource) {
      synchronized (this.propertySourceList) {
         this.removeIfPresent(propertySource);
         this.propertySourceList.add(0, propertySource);
      }
   }

   public void addLast(PropertySource<?> propertySource) {
      synchronized (this.propertySourceList) {
         this.removeIfPresent(propertySource);
         this.propertySourceList.add(propertySource);
      }
   }

   public void addBefore(String relativePropertySourceName, PropertySource<?> propertySource) {
      this.assertLegalRelativeAddition(relativePropertySourceName, propertySource);
      synchronized (this.propertySourceList) {
         this.removeIfPresent(propertySource);
         int index = this.assertPresentAndGetIndex(relativePropertySourceName);
         this.addAtIndex(index, propertySource);
      }
   }

   public void addAfter(String relativePropertySourceName, PropertySource<?> propertySource) {
      this.assertLegalRelativeAddition(relativePropertySourceName, propertySource);
      synchronized (this.propertySourceList) {
         this.removeIfPresent(propertySource);
         int index = this.assertPresentAndGetIndex(relativePropertySourceName);
         this.addAtIndex(index + 1, propertySource);
      }
   }

   public int precedenceOf(PropertySource<?> propertySource) {
      return this.propertySourceList.indexOf(propertySource);
   }

   public @Nullable PropertySource<?> remove(String name) {
      synchronized (this.propertySourceList) {
         int index = this.propertySourceList.indexOf(PropertySource.named(name));
         return index != -1 ? this.propertySourceList.remove(index) : null;
      }
   }

   public void replace(String name, PropertySource<?> propertySource) {
      synchronized (this.propertySourceList) {
         int index = this.assertPresentAndGetIndex(name);
         this.propertySourceList.set(index, propertySource);
      }
   }

   public int size() {
      return this.propertySourceList.size();
   }

   @Override
   public String toString() {
      return this.propertySourceList.toString();
   }

   protected void assertLegalRelativeAddition(String relativePropertySourceName, PropertySource<?> propertySource) {
      String newPropertySourceName = propertySource.getName();
      if (relativePropertySourceName.equals(newPropertySourceName)) {
         throw new IllegalArgumentException("PropertySource named '" + newPropertySourceName + "' cannot be added relative to itself");
      }
   }

   protected void removeIfPresent(PropertySource<?> propertySource) {
      this.propertySourceList.remove(propertySource);
   }

   private void addAtIndex(int index, PropertySource<?> propertySource) {
      this.removeIfPresent(propertySource);
      this.propertySourceList.add(index, propertySource);
   }

   private int assertPresentAndGetIndex(String name) {
      int index = this.propertySourceList.indexOf(PropertySource.named(name));
      if (index == -1) {
         throw new IllegalArgumentException("PropertySource named '" + name + "' does not exist");
      } else {
         return index;
      }
   }
}
