package org.springframework.ui;

import java.util.Collection;
import java.util.Map;
import org.jspecify.annotations.Nullable;

public interface Model {
   Model addAttribute(String attributeName, @Nullable Object attributeValue);

   Model addAttribute(Object attributeValue);

   Model addAllAttributes(Collection<?> attributeValues);

   Model addAllAttributes(Map<String, ?> attributes);

   Model mergeAttributes(Map<String, ?> attributes);

   boolean containsAttribute(String attributeName);

   @Nullable Object getAttribute(String attributeName);

   Map<String, Object> asMap();
}
