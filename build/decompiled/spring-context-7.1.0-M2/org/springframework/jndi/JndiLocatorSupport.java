package org.springframework.jndi;

import javax.naming.NamingException;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public abstract class JndiLocatorSupport extends JndiAccessor {
   public static final String CONTAINER_PREFIX = "java:comp/env/";
   private boolean resourceRef = false;

   public void setResourceRef(boolean resourceRef) {
      this.resourceRef = resourceRef;
   }

   public boolean isResourceRef() {
      return this.resourceRef;
   }

   protected Object lookup(String jndiName) throws NamingException {
      return this.lookup(jndiName, null);
   }

   protected <T> T lookup(String jndiName, @Nullable Class<T> requiredType) throws NamingException {
      Assert.notNull(jndiName, "'jndiName' must not be null");
      String convertedName = this.convertJndiName(jndiName);

      T jndiObject;
      try {
         jndiObject = this.getJndiTemplate().lookup(convertedName, requiredType);
      } catch (NamingException ex) {
         if (convertedName.equals(jndiName)) {
            throw ex;
         }

         if (this.logger.isDebugEnabled()) {
            this.logger.debug("Converted JNDI name [" + convertedName + "] not found - trying original name [" + jndiName + "]. " + ex);
         }

         jndiObject = this.getJndiTemplate().lookup(jndiName, requiredType);
      }

      if (this.logger.isDebugEnabled()) {
         this.logger.debug("Located object with JNDI name [" + convertedName + "]");
      }

      return jndiObject;
   }

   protected String convertJndiName(String jndiName) {
      if (this.isResourceRef() && !jndiName.startsWith("java:comp/env/") && jndiName.indexOf(58) == -1) {
         jndiName = "java:comp/env/" + jndiName;
      }

      return jndiName;
   }
}
