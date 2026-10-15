package org.springframework.beans.factory.config;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectFactory;

public interface Scope {
   Object get(String name, ObjectFactory<?> objectFactory);

   @Nullable Object remove(String name);

   void registerDestructionCallback(String name, Runnable callback);

   default @Nullable Object resolveContextualObject(String key) {
      return null;
   }

   default @Nullable String getConversationId() {
      return null;
   }
}
