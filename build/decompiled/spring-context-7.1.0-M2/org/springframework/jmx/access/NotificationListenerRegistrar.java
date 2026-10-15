package org.springframework.jmx.access;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Arrays;
import java.util.Map;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;
import javax.management.remote.JMXServiceURL;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jmx.JmxException;
import org.springframework.jmx.MBeanServerNotFoundException;
import org.springframework.jmx.support.NotificationListenerHolder;
import org.springframework.util.CollectionUtils;

public class NotificationListenerRegistrar extends NotificationListenerHolder implements InitializingBean, DisposableBean {
   protected final Log logger = LogFactory.getLog(this.getClass());
   private final ConnectorDelegate connector = new ConnectorDelegate();
   private @Nullable MBeanServerConnection server;
   private @Nullable JMXServiceURL serviceUrl;
   private @Nullable Map<String, ?> environment;
   private @Nullable String agentId;
   private ObjectName @Nullable [] actualObjectNames;

   public void setServer(MBeanServerConnection server) {
      this.server = server;
   }

   public void setEnvironment(@Nullable Map<String, ?> environment) {
      this.environment = environment;
   }

   public @Nullable Map<String, ?> getEnvironment() {
      return this.environment;
   }

   public void setServiceUrl(String url) throws MalformedURLException {
      this.serviceUrl = new JMXServiceURL(url);
   }

   public void setAgentId(String agentId) {
      this.agentId = agentId;
   }

   public void afterPropertiesSet() {
      if (this.getNotificationListener() == null) {
         throw new IllegalArgumentException("Property 'notificationListener' is required");
      }

      if (CollectionUtils.isEmpty(this.mappedObjectNames)) {
         throw new IllegalArgumentException("Property 'mappedObjectName' is required");
      }

      this.prepare();
   }

   public void prepare() {
      if (this.server == null) {
         this.server = this.connector.connect(this.serviceUrl, this.environment, this.agentId);
      }

      try {
         this.actualObjectNames = this.getResolvedObjectNames();
         if (this.actualObjectNames != null) {
            if (this.logger.isDebugEnabled()) {
               this.logger.debug("Registering NotificationListener for MBeans " + Arrays.toString(this.actualObjectNames));
            }

            for (ObjectName actualObjectName : this.actualObjectNames) {
               this.server.addNotificationListener(actualObjectName, this.getNotificationListener(), this.getNotificationFilter(), this.getHandback());
            }
         }
      } catch (IOException ex) {
         throw new MBeanServerNotFoundException("Could not connect to remote MBeanServer at URL [" + this.serviceUrl + "]", ex);
      } catch (Exception ex) {
         throw new JmxException("Unable to register NotificationListener", ex);
      }
   }

   public void destroy() {
      try {
         if (this.server != null && this.actualObjectNames != null) {
            for (ObjectName actualObjectName : this.actualObjectNames) {
               try {
                  this.server.removeNotificationListener(actualObjectName, this.getNotificationListener(), this.getNotificationFilter(), this.getHandback());
               } catch (Exception ex) {
                  if (this.logger.isDebugEnabled()) {
                     this.logger.debug("Unable to unregister NotificationListener", ex);
                  }
               }
            }
         }
      } finally {
         this.connector.close();
      }
   }
}
