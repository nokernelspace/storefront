package org.springframework.jmx.support;

import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jmx.MBeanServerNotFoundException;

public class MBeanServerFactoryBean implements FactoryBean<MBeanServer>, InitializingBean, DisposableBean {
   protected final Log logger = LogFactory.getLog(this.getClass());
   private boolean locateExistingServerIfPossible = false;
   private @Nullable String agentId;
   private @Nullable String defaultDomain;
   private boolean registerWithFactory = true;
   private @Nullable MBeanServer server;
   private boolean newlyRegistered = false;

   public void setLocateExistingServerIfPossible(boolean locateExistingServerIfPossible) {
      this.locateExistingServerIfPossible = locateExistingServerIfPossible;
   }

   public void setAgentId(String agentId) {
      this.agentId = agentId;
   }

   public void setDefaultDomain(String defaultDomain) {
      this.defaultDomain = defaultDomain;
   }

   public void setRegisterWithFactory(boolean registerWithFactory) {
      this.registerWithFactory = registerWithFactory;
   }

   public void afterPropertiesSet() throws MBeanServerNotFoundException {
      if (this.locateExistingServerIfPossible || this.agentId != null) {
         try {
            this.server = this.locateMBeanServer(this.agentId);
         } catch (MBeanServerNotFoundException ex) {
            if (this.agentId != null) {
               throw ex;
            }

            this.logger.debug("No existing MBeanServer found - creating new one");
         }
      }

      if (this.server == null) {
         this.server = this.createMBeanServer(this.defaultDomain, this.registerWithFactory);
         this.newlyRegistered = this.registerWithFactory;
      }
   }

   protected MBeanServer locateMBeanServer(@Nullable String agentId) throws MBeanServerNotFoundException {
      return JmxUtils.locateMBeanServer(agentId);
   }

   protected MBeanServer createMBeanServer(@Nullable String defaultDomain, boolean registerWithFactory) {
      return registerWithFactory ? MBeanServerFactory.createMBeanServer(defaultDomain) : MBeanServerFactory.newMBeanServer(defaultDomain);
   }

   public @Nullable MBeanServer getObject() {
      return this.server;
   }

   public Class<? extends MBeanServer> getObjectType() {
      return (Class<? extends MBeanServer>)(this.server != null ? this.server.getClass() : MBeanServer.class);
   }

   public boolean isSingleton() {
      return true;
   }

   public void destroy() {
      if (this.newlyRegistered) {
         MBeanServerFactory.releaseMBeanServer(this.server);
      }
   }
}
