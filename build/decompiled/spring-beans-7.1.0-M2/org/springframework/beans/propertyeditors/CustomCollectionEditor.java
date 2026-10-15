package org.springframework.beans.propertyeditors;

import java.beans.PropertyEditorSupport;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

public class CustomCollectionEditor extends PropertyEditorSupport {
   private final Class<? extends Collection> collectionType;
   private final boolean nullAsEmptyCollection;

   public CustomCollectionEditor(Class<? extends Collection> collectionType) {
      this(collectionType, false);
   }

   public CustomCollectionEditor(Class<? extends Collection> collectionType, boolean nullAsEmptyCollection) {
      Assert.notNull(collectionType, "Collection type is required");
      if (!Collection.class.isAssignableFrom(collectionType)) {
         throw new IllegalArgumentException("Collection type [" + collectionType.getName() + "] does not implement [java.util.Collection]");
      }

      this.collectionType = collectionType;
      this.nullAsEmptyCollection = nullAsEmptyCollection;
   }

   @Override
   public void setAsText(String text) throws IllegalArgumentException {
      this.setValue(text);
   }

   @Override
   public void setValue(@Nullable Object value) {
      if (value == null && this.nullAsEmptyCollection) {
         super.setValue(this.createCollection(this.collectionType, 0));
      } else if (value != null && (!this.collectionType.isInstance(value) || this.alwaysCreateNewCollection())) {
         if (value instanceof Collection<?> source) {
            Collection<Object> target = this.createCollection(this.collectionType, source.size());

            for (Object elem : source) {
               target.add(this.convertElement(elem));
            }

            super.setValue(target);
         } else if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            Collection<Object> target = this.createCollection(this.collectionType, length);

            for (int i = 0; i < length; i++) {
               target.add(this.convertElement(Array.get(value, i)));
            }

            super.setValue(target);
         } else {
            Collection<Object> target = this.createCollection(this.collectionType, 1);
            target.add(this.convertElement(value));
            super.setValue(target);
         }
      } else {
         super.setValue(value);
      }
   }

   protected Collection<Object> createCollection(Class<? extends Collection> collectionType, int initialCapacity) {
      if (!collectionType.isInterface()) {
         try {
            return (Collection<Object>)ReflectionUtils.accessibleConstructor(collectionType, new Class[0]).newInstance();
         } catch (Throwable ex) {
            throw new IllegalArgumentException("Could not instantiate collection class: " + collectionType.getName(), ex);
         }
      } else if (List.class == collectionType) {
         return new ArrayList<>(initialCapacity);
      } else {
         return SortedSet.class == collectionType ? new TreeSet<>() : new LinkedHashSet<>(initialCapacity);
      }
   }

   protected boolean alwaysCreateNewCollection() {
      return false;
   }

   protected Object convertElement(Object element) {
      return element;
   }

   @Override
   public @Nullable String getAsText() {
      return null;
   }
}
