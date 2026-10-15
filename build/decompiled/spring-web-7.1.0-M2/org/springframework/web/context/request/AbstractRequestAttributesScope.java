package org.springframework.web.context.request;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;

public abstract class AbstractRequestAttributesScope implements Scope {
   public Object get(String name, ObjectFactory<?> objectFactory) {
      RequestAttributes attributes = RequestContextHolder.currentRequestAttributes();
      Object scopedObject = attributes.getAttribute(name, this.getScope());
      if (scopedObject == null) {
         scopedObject = objectFactory.getObject();
         attributes.setAttribute(name, scopedObject, this.getScope());
         Object retrievedObject = attributes.getAttribute(name, this.getScope());
         if (retrievedObject != null) {
            scopedObject = retrievedObject;
         }
      }

      return scopedObject;
   }

   public @Nullable Object remove(String name) {
      RequestAttributes attributes = RequestContextHolder.currentRequestAttributes();
      Object scopedObject = attributes.getAttribute(name, this.getScope());
      if (scopedObject != null) {
         attributes.removeAttribute(name, this.getScope());
         return scopedObject;
      } else {
         return null;
      }
   }

   public void registerDestructionCallback(String name, Runnable callback) {
      RequestAttributes attributes = RequestContextHolder.currentRequestAttributes();
      attributes.registerDestructionCallback(name, callback, this.getScope());
   }

   public @Nullable Object resolveContextualObject(String key) {
      RequestAttributes attributes = RequestContextHolder.currentRequestAttributes();
      return attributes.resolveReference(key);
   }

   protected abstract int getScope();
}
