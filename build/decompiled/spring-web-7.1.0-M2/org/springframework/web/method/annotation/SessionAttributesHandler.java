package org.springframework.web.method.annotation;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.SessionAttributes;
import org.springframework.web.bind.support.SessionAttributeStore;
import org.springframework.web.context.request.WebRequest;

public class SessionAttributesHandler {
   public static final String SESSION_KNOWN_ATTRIBUTE = SessionAttributesHandler.class.getName() + ".KNOWN";
   private final Set<String> attributeNames = new HashSet<>();
   private final Set<Class<?>> attributeTypes = new HashSet<>();
   private final Set<String> knownAttributeNames = ConcurrentHashMap.newKeySet(4);
   private final SessionAttributeStore sessionAttributeStore;

   public SessionAttributesHandler(Class<?> handlerType, SessionAttributeStore sessionAttributeStore) {
      Assert.notNull(sessionAttributeStore, "SessionAttributeStore may not be null");
      this.sessionAttributeStore = sessionAttributeStore;
      SessionAttributes ann = (SessionAttributes)AnnotatedElementUtils.findMergedAnnotation(handlerType, SessionAttributes.class);
      if (ann != null) {
         Collections.addAll(this.attributeNames, ann.names());
         Collections.addAll(this.attributeTypes, ann.types());
      }

      this.knownAttributeNames.addAll(this.attributeNames);
   }

   public boolean hasSessionAttributes() {
      return !this.attributeNames.isEmpty() || !this.attributeTypes.isEmpty();
   }

   public boolean isHandlerSessionAttribute(String attributeName, Class<?> attributeType) {
      Assert.notNull(attributeName, "Attribute name must not be null");
      if (this.attributeTypes.contains(attributeType)) {
         this.knownAttributeNames.add(attributeName);
         return true;
      } else {
         return this.attributeNames.contains(attributeName);
      }
   }

   public void storeAttributes(WebRequest request, Map<String, ?> attributes) {
      attributes.forEach((name, value) -> {
         if (value != null && this.isHandlerSessionAttribute(name, value.getClass())) {
            this.sessionAttributeStore.storeAttribute(request, name, value);
         }
      });
      if (!this.attributeTypes.isEmpty()) {
         this.sessionAttributeStore.storeAttribute(request, SESSION_KNOWN_ATTRIBUTE, StringUtils.toStringArray(this.knownAttributeNames));
      }
   }

   public Map<String, Object> retrieveAttributes(WebRequest request) {
      if (!this.attributeTypes.isEmpty()
         && this.sessionAttributeStore.retrieveAttribute(request, SESSION_KNOWN_ATTRIBUTE) instanceof String[] retrievedAttributeNames) {
         this.knownAttributeNames.addAll(Arrays.asList(retrievedAttributeNames));
      }

      Map<String, Object> attributes = new HashMap<>();

      for (String name : this.knownAttributeNames) {
         Object value = this.sessionAttributeStore.retrieveAttribute(request, name);
         if (value != null) {
            attributes.put(name, value);
         }
      }

      return attributes;
   }

   public void cleanupAttributes(WebRequest request) {
      for (String attributeName : this.knownAttributeNames) {
         this.sessionAttributeStore.cleanupAttribute(request, attributeName);
      }
   }

   @Nullable Object retrieveAttribute(WebRequest request, String attributeName) {
      return this.sessionAttributeStore.retrieveAttribute(request, attributeName);
   }
}
