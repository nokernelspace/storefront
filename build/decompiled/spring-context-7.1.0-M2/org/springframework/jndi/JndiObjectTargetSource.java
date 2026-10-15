package org.springframework.jndi;

import javax.naming.NamingException;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.TargetSource;

public class JndiObjectTargetSource extends JndiObjectLocator implements TargetSource {
   private boolean lookupOnStartup = true;
   private boolean cache = true;
   private @Nullable Object cachedObject;
   private @Nullable Class<?> targetClass;

   public void setLookupOnStartup(boolean lookupOnStartup) {
      this.lookupOnStartup = lookupOnStartup;
   }

   public void setCache(boolean cache) {
      this.cache = cache;
   }

   @Override
   public void afterPropertiesSet() throws NamingException {
      super.afterPropertiesSet();
      if (this.lookupOnStartup) {
         Object object = this.lookup();
         if (this.cache) {
            this.cachedObject = object;
         } else {
            this.targetClass = object.getClass();
         }
      }
   }

   public @Nullable Class<?> getTargetClass() {
      if (this.cachedObject != null) {
         return this.cachedObject.getClass();
      } else {
         return this.targetClass != null ? this.targetClass : this.getExpectedType();
      }
   }

   public boolean isStatic() {
      return this.cachedObject != null;
   }

   public @Nullable Object getTarget() {
      try {
         if (!this.lookupOnStartup && this.cache) {
            synchronized (this) {
               if (this.cachedObject == null) {
                  this.cachedObject = this.lookup();
               }

               return this.cachedObject;
            }
         } else {
            return this.cachedObject != null ? this.cachedObject : this.lookup();
         }
      } catch (NamingException ex) {
         throw new JndiLookupFailureException("JndiObjectTargetSource failed to obtain new target object", ex);
      }
   }
}
