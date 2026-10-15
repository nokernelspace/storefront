package org.springframework.ui;

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Conventions;
import org.springframework.util.Assert;

public class ConcurrentModel extends ConcurrentHashMap<String, Object> implements Model {
   public ConcurrentModel() {
   }

   public ConcurrentModel(String attributeName, Object attributeValue) {
      this.addAttribute(attributeName, attributeValue);
   }

   public ConcurrentModel(Object attributeValue) {
      this.addAttribute(attributeValue);
   }

   public @Nullable Object put(String key, @Nullable Object value) {
      return value != null ? super.put(key, value) : this.remove(key);
   }

   @Override
   public void putAll(Map<? extends String, ?> map) {
      for (Entry<? extends String, ?> entry : map.entrySet()) {
         this.put(entry.getKey(), entry.getValue());
      }
   }

   public ConcurrentModel addAttribute(String attributeName, @Nullable Object attributeValue) {
      Assert.notNull(attributeName, "Model attribute name must not be null");
      this.put(attributeName, attributeValue);
      return this;
   }

   public ConcurrentModel addAttribute(Object attributeValue) {
      Assert.notNull(attributeValue, "Model attribute value must not be null");
      return attributeValue instanceof Collection<?> collection && collection.isEmpty()
         ? this
         : this.addAttribute(Conventions.getVariableName(attributeValue), attributeValue);
   }

   public ConcurrentModel addAllAttributes(@Nullable Collection<?> attributeValues) {
      if (attributeValues != null) {
         for (Object attributeValue : attributeValues) {
            this.addAttribute(attributeValue);
         }
      }

      return this;
   }

   public ConcurrentModel addAllAttributes(@Nullable Map<String, ?> attributes) {
      if (attributes != null) {
         this.putAll(attributes);
      }

      return this;
   }

   public ConcurrentModel mergeAttributes(@Nullable Map<String, ?> attributes) {
      if (attributes != null) {
         attributes.forEach((key, value) -> {
            if (!this.containsKey(key)) {
               this.put(key, value);
            }
         });
      }

      return this;
   }

   @Override
   public boolean containsAttribute(String attributeName) {
      return this.containsKey(attributeName);
   }

   @Override
   public @Nullable Object getAttribute(String attributeName) {
      return this.get(attributeName);
   }

   @Override
   public Map<String, Object> asMap() {
      return this;
   }
}
