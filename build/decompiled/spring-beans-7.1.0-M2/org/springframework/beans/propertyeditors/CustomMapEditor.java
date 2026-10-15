package org.springframework.beans.propertyeditors;

import java.beans.PropertyEditorSupport;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

public class CustomMapEditor extends PropertyEditorSupport {
   private final Class<? extends Map> mapType;
   private final boolean nullAsEmptyMap;

   public CustomMapEditor(Class<? extends Map> mapType) {
      this(mapType, false);
   }

   public CustomMapEditor(Class<? extends Map> mapType, boolean nullAsEmptyMap) {
      Assert.notNull(mapType, "Map type is required");
      if (!Map.class.isAssignableFrom(mapType)) {
         throw new IllegalArgumentException("Map type [" + mapType.getName() + "] does not implement [java.util.Map]");
      }

      this.mapType = mapType;
      this.nullAsEmptyMap = nullAsEmptyMap;
   }

   @Override
   public void setAsText(String text) throws IllegalArgumentException {
      this.setValue(text);
   }

   @Override
   public void setValue(@Nullable Object value) {
      if (value == null && this.nullAsEmptyMap) {
         super.setValue(this.createMap(this.mapType, 0));
      } else if (value != null && (!this.mapType.isInstance(value) || this.alwaysCreateNewMap())) {
         if (!(value instanceof Map<?, ?> source)) {
            throw new IllegalArgumentException("Value cannot be converted to Map: " + value);
         }

         Map<Object, Object> target = this.createMap(this.mapType, source.size());
         source.forEach((key, val) -> target.put(this.convertKey(key), this.convertValue(val)));
         super.setValue(target);
      } else {
         super.setValue(value);
      }
   }

   protected Map<Object, Object> createMap(Class<? extends Map> mapType, int initialCapacity) {
      if (!mapType.isInterface()) {
         try {
            return (Map<Object, Object>)ReflectionUtils.accessibleConstructor(mapType, new Class[0]).newInstance();
         } catch (Throwable ex) {
            throw new IllegalArgumentException("Could not instantiate map class: " + mapType.getName(), ex);
         }
      } else {
         return SortedMap.class == mapType ? new TreeMap<>() : new LinkedHashMap<>(initialCapacity);
      }
   }

   protected boolean alwaysCreateNewMap() {
      return false;
   }

   protected Object convertKey(Object key) {
      return key;
   }

   protected Object convertValue(Object value) {
      return value;
   }

   @Override
   public @Nullable String getAsText() {
      return null;
   }
}
