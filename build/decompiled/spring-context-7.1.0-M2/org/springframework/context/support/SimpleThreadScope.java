package org.springframework.context.support;

import java.util.HashMap;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.Scope;
import org.springframework.core.NamedThreadLocal;

public class SimpleThreadScope implements Scope {
   private static final Log logger = LogFactory.getLog(SimpleThreadScope.class);
   private final ThreadLocal<Map<String, Object>> threadScope = NamedThreadLocal.withInitial("SimpleThreadScope", HashMap::new);

   public Object get(String name, ObjectFactory<?> objectFactory) {
      Map<String, Object> scope = this.threadScope.get();
      Object scopedObject = scope.get(name);
      if (scopedObject == null) {
         scopedObject = objectFactory.getObject();
         scope.put(name, scopedObject);
      }

      return scopedObject;
   }

   public @Nullable Object remove(String name) {
      Map<String, Object> scope = this.threadScope.get();
      return scope.remove(name);
   }

   public void registerDestructionCallback(String name, Runnable callback) {
      logger.warn("SimpleThreadScope does not support destruction callbacks. Consider using RequestScope in a web environment.");
   }

   public String getConversationId() {
      return Thread.currentThread().getName();
   }
}
