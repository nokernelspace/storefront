package org.springframework.web.bind.support;

import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.web.context.request.WebRequest;

public class DefaultSessionAttributeStore implements SessionAttributeStore {
   private String attributeNamePrefix = "";

   public void setAttributeNamePrefix(@Nullable String attributeNamePrefix) {
      this.attributeNamePrefix = attributeNamePrefix != null ? attributeNamePrefix : "";
   }

   @Override
   public void storeAttribute(WebRequest request, String attributeName, Object attributeValue) {
      Assert.notNull(request, "WebRequest must not be null");
      Assert.notNull(attributeName, "Attribute name must not be null");
      Assert.notNull(attributeValue, "Attribute value must not be null");
      String storeAttributeName = this.getAttributeNameInSession(request, attributeName);
      request.setAttribute(storeAttributeName, attributeValue, 1);
   }

   @Override
   public @Nullable Object retrieveAttribute(WebRequest request, String attributeName) {
      Assert.notNull(request, "WebRequest must not be null");
      Assert.notNull(attributeName, "Attribute name must not be null");
      String storeAttributeName = this.getAttributeNameInSession(request, attributeName);
      return request.getAttribute(storeAttributeName, 1);
   }

   @Override
   public void cleanupAttribute(WebRequest request, String attributeName) {
      Assert.notNull(request, "WebRequest must not be null");
      Assert.notNull(attributeName, "Attribute name must not be null");
      String storeAttributeName = this.getAttributeNameInSession(request, attributeName);
      request.removeAttribute(storeAttributeName, 1);
   }

   protected String getAttributeNameInSession(WebRequest request, String attributeName) {
      return this.attributeNamePrefix + attributeName;
   }
}
