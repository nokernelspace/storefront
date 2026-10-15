package org.springframework.scheduling.concurrent;

import java.util.Properties;
import java.util.concurrent.ThreadFactory;
import javax.naming.NamingException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jndi.JndiLocatorDelegate;
import org.springframework.jndi.JndiTemplate;

public class DefaultManagedAwareThreadFactory extends CustomizableThreadFactory implements InitializingBean {
   protected final Log logger = LogFactory.getLog(this.getClass());
   private final JndiLocatorDelegate jndiLocator = new JndiLocatorDelegate();
   private @Nullable String jndiName = "java:comp/DefaultManagedThreadFactory";
   private @Nullable ThreadFactory threadFactory;

   public void setJndiTemplate(JndiTemplate jndiTemplate) {
      this.jndiLocator.setJndiTemplate(jndiTemplate);
   }

   public void setJndiEnvironment(Properties jndiEnvironment) {
      this.jndiLocator.setJndiEnvironment(jndiEnvironment);
   }

   public void setResourceRef(boolean resourceRef) {
      this.jndiLocator.setResourceRef(resourceRef);
   }

   public void setJndiName(String jndiName) {
      this.jndiName = jndiName;
   }

   public void afterPropertiesSet() throws NamingException {
      if (this.jndiName != null) {
         try {
            this.threadFactory = this.jndiLocator.lookup(this.jndiName, ThreadFactory.class);
         } catch (NamingException ex) {
            if (this.logger.isTraceEnabled()) {
               this.logger.trace("Failed to retrieve [" + this.jndiName + "] from JNDI", ex);
            }

            this.logger.info("Could not find default managed thread factory in JNDI - proceeding with default local thread factory");
         }
      }
   }

   @Override
   public Thread newThread(Runnable runnable) {
      return this.threadFactory != null ? this.threadFactory.newThread(runnable) : super.newThread(runnable);
   }
}
