package org.springframework.jmx.support;

import java.util.LinkedHashSet;
import java.util.Set;
import javax.management.InstanceAlreadyExistsException;
import javax.management.InstanceNotFoundException;
import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public class MBeanRegistrationSupport {
   protected final Log logger = LogFactory.getLog(this.getClass());
   protected @Nullable MBeanServer server;
   private final Set<ObjectName> registeredBeans = new LinkedHashSet<>();
   private RegistrationPolicy registrationPolicy = RegistrationPolicy.FAIL_ON_EXISTING;

   public void setServer(@Nullable MBeanServer server) {
      this.server = server;
   }

   public final @Nullable MBeanServer getServer() {
      return this.server;
   }

   public void setRegistrationPolicy(RegistrationPolicy registrationPolicy) {
      Assert.notNull(registrationPolicy, "RegistrationPolicy must not be null");
      this.registrationPolicy = registrationPolicy;
   }

   protected void doRegister(Object mbean, ObjectName objectName) throws JMException {
      Assert.state(this.server != null, "No MBeanServer set");
      ObjectName actualObjectName;
      synchronized (this.registeredBeans) {
         ObjectInstance registeredBean = null;

         try {
            registeredBean = this.server.registerMBean(mbean, objectName);
         } catch (InstanceAlreadyExistsException ex) {
            if (this.registrationPolicy == RegistrationPolicy.IGNORE_EXISTING) {
               if (this.logger.isDebugEnabled()) {
                  this.logger.debug("Ignoring existing MBean at [" + objectName + "]");
               }
            } else {
               if (this.registrationPolicy != RegistrationPolicy.REPLACE_EXISTING) {
                  throw ex;
               }

               try {
                  if (this.logger.isDebugEnabled()) {
                     this.logger.debug("Replacing existing MBean at [" + objectName + "]");
                  }

                  this.server.unregisterMBean(objectName);
                  registeredBean = this.server.registerMBean(mbean, objectName);
               } catch (InstanceNotFoundException ex2) {
                  if (this.logger.isInfoEnabled()) {
                     this.logger.info("Unable to replace existing MBean at [" + objectName + "]", ex2);
                  }

                  throw ex;
               }
            }
         }

         actualObjectName = registeredBean != null ? registeredBean.getObjectName() : null;
         if (actualObjectName == null) {
            actualObjectName = objectName;
         }

         this.registeredBeans.add(actualObjectName);
      }

      this.onRegister(actualObjectName, mbean);
   }

   protected void unregisterBeans() {
      Set<ObjectName> snapshot;
      synchronized (this.registeredBeans) {
         snapshot = new LinkedHashSet<>(this.registeredBeans);
      }

      if (!snapshot.isEmpty()) {
         this.logger.debug("Unregistering JMX-exposed beans");

         for (ObjectName objectName : snapshot) {
            this.doUnregister(objectName);
         }
      }
   }

   protected void doUnregister(ObjectName objectName) {
      Assert.state(this.server != null, "No MBeanServer set");
      boolean actuallyUnregistered = false;
      synchronized (this.registeredBeans) {
         if (this.registeredBeans.remove(objectName)) {
            try {
               if (this.server.isRegistered(objectName)) {
                  this.server.unregisterMBean(objectName);
                  actuallyUnregistered = true;
               } else if (this.logger.isInfoEnabled()) {
                  this.logger
                     .info(
                        "Could not unregister MBean [" + objectName + "] as said MBean is not registered (perhaps already unregistered by an external process)"
                     );
               }
            } catch (JMException ex) {
               if (this.logger.isInfoEnabled()) {
                  this.logger.info("Could not unregister MBean [" + objectName + "]", ex);
               }
            }
         }
      }

      if (actuallyUnregistered) {
         this.onUnregister(objectName);
      }
   }

   protected final ObjectName[] getRegisteredObjectNames() {
      synchronized (this.registeredBeans) {
         return this.registeredBeans.toArray(new ObjectName[0]);
      }
   }

   protected void onRegister(ObjectName objectName, Object mbean) {
      this.onRegister(objectName);
   }

   protected void onRegister(ObjectName objectName) {
   }

   protected void onUnregister(ObjectName objectName) {
   }
}
