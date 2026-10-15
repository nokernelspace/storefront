package org.springframework.web.context.request;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectFactory;

public class SessionScope extends AbstractRequestAttributesScope {
   @Override
   protected int getScope() {
      return 1;
   }

   public String getConversationId() {
      return RequestContextHolder.currentRequestAttributes().getSessionId();
   }

   @Override
   public Object get(String name, ObjectFactory<?> objectFactory) {
      synchronized (RequestContextHolder.currentRequestAttributes().getSessionMutex()) {
         return super.get(name, objectFactory);
      }
   }

   @Override
   public @Nullable Object remove(String name) {
      synchronized (RequestContextHolder.currentRequestAttributes().getSessionMutex()) {
         return super.remove(name);
      }
   }
}
