package org.springframework.jndi;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import org.jspecify.annotations.Nullable;
import org.springframework.core.SpringProperties;

public class JndiLocatorDelegate extends JndiLocatorSupport {
   public static final String IGNORE_JNDI_PROPERTY_NAME = "spring.jndi.ignore";
   private static final boolean shouldIgnoreDefaultJndiEnvironment = SpringProperties.getFlag("spring.jndi.ignore");

   @Override
   public Object lookup(String jndiName) throws NamingException {
      return super.lookup(jndiName);
   }

   @Override
   public <T> T lookup(String jndiName, @Nullable Class<T> requiredType) throws NamingException {
      return super.lookup(jndiName, requiredType);
   }

   public static JndiLocatorDelegate createDefaultResourceRefLocator() {
      JndiLocatorDelegate jndiLocator = new JndiLocatorDelegate();
      jndiLocator.setResourceRef(true);
      return jndiLocator;
   }

   public static boolean isDefaultJndiEnvironmentAvailable() {
      if (shouldIgnoreDefaultJndiEnvironment) {
         return false;
      }

      try {
         new InitialContext().getEnvironment();
         return true;
      } catch (Throwable ex) {
         return false;
      }
   }
}
