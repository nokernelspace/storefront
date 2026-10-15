package org.springframework.ui;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Conventions;
import org.springframework.util.Assert;

public class ModelMap extends LinkedHashMap<String, Object> {
   public ModelMap() {
   }

   public ModelMap(String attributeName, @Nullable Object attributeValue) {
      this.addAttribute(attributeName, attributeValue);
   }

   public ModelMap(Object attributeValue) {
      this.addAttribute(attributeValue);
   }

   public ModelMap addAttribute(String attributeName, @Nullable Object attributeValue) {
      Assert.notNull(attributeName, "Model attribute name must not be null");
      this.put(attributeName, attributeValue);
      return this;
   }

   public ModelMap addAttribute(Object attributeValue) {
      Assert.notNull(attributeValue, "Model object must not be null");
      return attributeValue instanceof Collection<?> collection && collection.isEmpty()
         ? this
         : this.addAttribute(Conventions.getVariableName(attributeValue), attributeValue);
   }

   public ModelMap addAllAttributes(@Nullable Collection<?> attributeValues) {
      if (attributeValues != null) {
         for (Object attributeValue : attributeValues) {
            this.addAttribute(attributeValue);
         }
      }

      return this;
   }

   public ModelMap addAllAttributes(@Nullable Map<String, ?> attributes) {
      if (attributes != null) {
         this.putAll((Map<? extends String, ? extends Object>)attributes);
      }

      return this;
   }

   public ModelMap mergeAttributes(@Nullable Map<String, ?> attributes) {
      if (attributes != null) {
         attributes.forEach((key, value) -> {
            if (!this.containsKey(key)) {
               this.put(key, value);
            }
         });
      }

      return this;
   }

   public boolean containsAttribute(String attributeName) {
      return this.containsKey(attributeName);
   }

   public @Nullable Object getAttribute(String attributeName) {
      return this.get(attributeName);
   }
}
