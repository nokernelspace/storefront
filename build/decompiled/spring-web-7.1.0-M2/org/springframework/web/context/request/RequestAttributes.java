package org.springframework.web.context.request;

import org.jspecify.annotations.Nullable;

public interface RequestAttributes {
   int SCOPE_REQUEST = 0;
   int SCOPE_SESSION = 1;
   String REFERENCE_REQUEST = "request";
   String REFERENCE_SESSION = "session";

   @Nullable Object getAttribute(String name, int scope);

   void setAttribute(String name, Object value, int scope);

   void removeAttribute(String name, int scope);

   String[] getAttributeNames(int scope);

   void registerDestructionCallback(String name, Runnable callback, int scope);

   @Nullable Object resolveReference(String key);

   String getSessionId();

   Object getSessionMutex();
}
