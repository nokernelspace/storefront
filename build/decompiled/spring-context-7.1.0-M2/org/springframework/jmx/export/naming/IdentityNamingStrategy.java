package org.springframework.jmx.export.naming;

import java.util.Hashtable;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import org.jspecify.annotations.Nullable;
import org.springframework.jmx.support.ObjectNameManager;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;

public class IdentityNamingStrategy implements ObjectNamingStrategy {
   public static final String TYPE_KEY = "type";
   public static final String HASH_CODE_KEY = "hashCode";

   @Override
   public ObjectName getObjectName(Object managedBean, @Nullable String beanKey) throws MalformedObjectNameException {
      String domain = ClassUtils.getPackageName(managedBean.getClass());
      Hashtable<String, String> keys = new Hashtable<>();
      keys.put("type", ClassUtils.getShortName(managedBean.getClass()));
      keys.put("hashCode", ObjectUtils.getIdentityHexString(managedBean));
      return ObjectNameManager.getInstance(domain, keys);
   }
}
