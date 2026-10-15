package org.springframework.jmx.support;

import java.util.Hashtable;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;

public final class ObjectNameManager {
   private ObjectNameManager() {
   }

   public static ObjectName getInstance(Object name) throws MalformedObjectNameException {
      if (name instanceof ObjectName objectName) {
         return objectName;
      } else if (name instanceof String text) {
         return getInstance(text);
      } else {
         throw new MalformedObjectNameException("Invalid ObjectName value type [" + name.getClass().getName() + "]: only ObjectName and String supported.");
      }
   }

   public static ObjectName getInstance(String objectName) throws MalformedObjectNameException {
      return ObjectName.getInstance(objectName);
   }

   public static ObjectName getInstance(String domainName, String key, String value) throws MalformedObjectNameException {
      return ObjectName.getInstance(domainName, key, value);
   }

   public static ObjectName getInstance(String domainName, Hashtable<String, String> properties) throws MalformedObjectNameException {
      return ObjectName.getInstance(domainName, properties);
   }
}
