package org.springframework.jndi;

import javax.naming.NamingException;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public abstract class JndiObjectLocator extends JndiLocatorSupport implements InitializingBean {
   private @Nullable String jndiName;
   private @Nullable Class<?> expectedType;

   public void setJndiName(@Nullable String jndiName) {
      this.jndiName = jndiName;
   }

   public @Nullable String getJndiName() {
      return this.jndiName;
   }

   public void setExpectedType(@Nullable Class<?> expectedType) {
      this.expectedType = expectedType;
   }

   public @Nullable Class<?> getExpectedType() {
      return this.expectedType;
   }

   public void afterPropertiesSet() throws IllegalArgumentException, NamingException {
      if (!StringUtils.hasLength(this.getJndiName())) {
         throw new IllegalArgumentException("Property 'jndiName' is required");
      }
   }

   protected Object lookup() throws NamingException {
      String jndiName = this.getJndiName();
      Assert.state(jndiName != null, "No JNDI name specified");
      return this.lookup(jndiName, this.getExpectedType());
   }
}
