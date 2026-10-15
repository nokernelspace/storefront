package org.springframework.ui;

import java.util.Collection;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public class ExtendedModelMap extends ModelMap implements Model {
   public ExtendedModelMap addAttribute(String attributeName, @Nullable Object attributeValue) {
      super.addAttribute(attributeName, attributeValue);
      return this;
   }

   public ExtendedModelMap addAttribute(Object attributeValue) {
      super.addAttribute(attributeValue);
      return this;
   }

   public ExtendedModelMap addAllAttributes(@Nullable Collection<?> attributeValues) {
      super.addAllAttributes(attributeValues);
      return this;
   }

   public ExtendedModelMap addAllAttributes(@Nullable Map<String, ?> attributes) {
      super.addAllAttributes(attributes);
      return this;
   }

   public ExtendedModelMap mergeAttributes(@Nullable Map<String, ?> attributes) {
      super.mergeAttributes(attributes);
      return this;
   }

   @Override
   public Map<String, Object> asMap() {
      return this;
   }
}
