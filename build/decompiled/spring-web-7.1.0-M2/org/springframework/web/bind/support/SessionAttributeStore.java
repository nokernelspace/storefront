package org.springframework.web.bind.support;

import org.jspecify.annotations.Nullable;
import org.springframework.web.context.request.WebRequest;

public interface SessionAttributeStore {
   void storeAttribute(WebRequest request, String attributeName, Object attributeValue);

   @Nullable Object retrieveAttribute(WebRequest request, String attributeName);

   void cleanupAttribute(WebRequest request, String attributeName);
}
