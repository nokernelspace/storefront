package org.springframework.jndi;

import java.util.Hashtable;
import java.util.Properties;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.util.CollectionUtils;

public class JndiTemplate {
   protected final Log logger = LogFactory.getLog(this.getClass());
   private @Nullable Properties environment;

   public JndiTemplate() {
   }

   public JndiTemplate(@Nullable Properties environment) {
      this.environment = environment;
   }

   public void setEnvironment(@Nullable Properties environment) {
      this.environment = environment;
   }

   public @Nullable Properties getEnvironment() {
      return this.environment;
   }

   public <T> @Nullable T execute(JndiCallback<T> contextCallback) throws NamingException {
      Context ctx = this.getContext();

      try {
         return contextCallback.doInContext(ctx);
      } finally {
         this.releaseContext(ctx);
      }
   }

   public Context getContext() throws NamingException {
      return this.createInitialContext();
   }

   public void releaseContext(@Nullable Context ctx) {
      if (ctx != null) {
         try {
            ctx.close();
         } catch (NamingException ex) {
            this.logger.debug("Could not close JNDI InitialContext", ex);
         }
      }
   }

   protected Context createInitialContext() throws NamingException {
      Hashtable<?, ?> icEnv = null;
      Properties env = this.getEnvironment();
      if (env != null) {
         icEnv = new Hashtable(env.size());
         CollectionUtils.mergePropertiesIntoMap(env, icEnv);
      }

      return new InitialContext(icEnv);
   }

   public Object lookup(final String name) throws NamingException {
      if (this.logger.isDebugEnabled()) {
         this.logger.debug("Looking up JNDI object with name [" + name + "]");
      }

      Object result = this.execute(ctx -> ctx.lookup(name));
      if (result == null) {
         throw new NameNotFoundException("JNDI object with [" + name + "] not found: JNDI implementation returned null");
      } else {
         return result;
      }
   }

   public <T> T lookup(String name, @Nullable Class<T> requiredType) throws NamingException {
      Object jndiObject = this.lookup(name);
      if (requiredType != null && !requiredType.isInstance(jndiObject)) {
         throw new TypeMismatchNamingException(name, requiredType, jndiObject.getClass());
      } else {
         return (T)jndiObject;
      }
   }

   public void bind(final String name, final Object object) throws NamingException {
      if (this.logger.isDebugEnabled()) {
         this.logger.debug("Binding JNDI object with name [" + name + "]");
      }

      this.execute(ctx -> {
         ctx.bind(name, object);
         return null;
      });
   }

   public void rebind(final String name, final Object object) throws NamingException {
      if (this.logger.isDebugEnabled()) {
         this.logger.debug("Rebinding JNDI object with name [" + name + "]");
      }

      this.execute(ctx -> {
         ctx.rebind(name, object);
         return null;
      });
   }

   public void unbind(final String name) throws NamingException {
      if (this.logger.isDebugEnabled()) {
         this.logger.debug("Unbinding JNDI object with name [" + name + "]");
      }

      this.execute(ctx -> {
         ctx.unbind(name);
         return null;
      });
   }
}
