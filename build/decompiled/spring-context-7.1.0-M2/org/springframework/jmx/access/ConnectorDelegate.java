package org.springframework.jmx.access;

import java.io.IOException;
import java.util.Map;
import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.jmx.MBeanServerNotFoundException;
import org.springframework.jmx.support.JmxUtils;

class ConnectorDelegate {
   private static final Log logger = LogFactory.getLog(ConnectorDelegate.class);
   private @Nullable JMXConnector connector;

   public MBeanServerConnection connect(@Nullable JMXServiceURL serviceUrl, @Nullable Map<String, ?> environment, @Nullable String agentId) throws MBeanServerNotFoundException {
      if (serviceUrl != null) {
         if (logger.isDebugEnabled()) {
            logger.debug("Connecting to remote MBeanServer at URL [" + serviceUrl + "]");
         }

         try {
            this.connector = JMXConnectorFactory.connect(serviceUrl, environment);
            return this.connector.getMBeanServerConnection();
         } catch (IOException ex) {
            throw new MBeanServerNotFoundException("Could not connect to remote MBeanServer [" + serviceUrl + "]", ex);
         }
      } else {
         logger.debug("Attempting to locate local MBeanServer");
         return JmxUtils.locateMBeanServer(agentId);
      }
   }

   public void close() {
      if (this.connector != null) {
         try {
            this.connector.close();
         } catch (IOException ex) {
            logger.debug("Could not close JMX connector", ex);
         }
      }
   }
}
