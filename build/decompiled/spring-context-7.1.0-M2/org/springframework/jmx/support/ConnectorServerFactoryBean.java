package org.springframework.jmx.support;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.management.JMException;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.remote.JMXConnectorServer;
import javax.management.remote.JMXConnectorServerFactory;
import javax.management.remote.JMXServiceURL;
import javax.management.remote.MBeanServerForwarder;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jmx.JmxException;
import org.springframework.util.CollectionUtils;

public class ConnectorServerFactoryBean extends MBeanRegistrationSupport implements FactoryBean<JMXConnectorServer>, InitializingBean, DisposableBean {
   public static final String DEFAULT_SERVICE_URL = "service:jmx:jmxmp://localhost:9875";
   private String serviceUrl = "service:jmx:jmxmp://localhost:9875";
   private final Map<String, Object> environment = new HashMap<>();
   private @Nullable MBeanServerForwarder forwarder;
   private @Nullable ObjectName objectName;
   private boolean threaded = false;
   private boolean daemon = false;
   private @Nullable JMXConnectorServer connectorServer;

   public void setServiceUrl(String serviceUrl) {
      this.serviceUrl = serviceUrl;
   }

   public void setEnvironment(@Nullable Properties environment) {
      CollectionUtils.mergePropertiesIntoMap(environment, this.environment);
   }

   public void setEnvironmentMap(@Nullable Map<String, ?> environment) {
      if (environment != null) {
         this.environment.putAll((Map<? extends String, ? extends Object>)environment);
      }
   }

   public void setForwarder(MBeanServerForwarder forwarder) {
      this.forwarder = forwarder;
   }

   public void setObjectName(Object objectName) throws MalformedObjectNameException {
      this.objectName = ObjectNameManager.getInstance(objectName);
   }

   public void setThreaded(boolean threaded) {
      this.threaded = threaded;
   }

   public void setDaemon(boolean daemon) {
      this.daemon = daemon;
   }

   public void afterPropertiesSet() throws JMException, IOException {
      if (this.server == null) {
         this.server = JmxUtils.locateMBeanServer();
      }

      JMXServiceURL url = new JMXServiceURL(this.serviceUrl);
      this.connectorServer = JMXConnectorServerFactory.newJMXConnectorServer(url, this.environment, this.server);
      if (this.forwarder != null) {
         this.connectorServer.setMBeanServerForwarder(this.forwarder);
      }

      if (this.objectName != null) {
         this.doRegister(this.connectorServer, this.objectName);
      }

      try {
         if (this.threaded) {
            final JMXConnectorServer serverToStart = this.connectorServer;
            Thread connectorThread = new Thread() {
               @Override
               public void run() {
                  try {
                     serverToStart.start();
                  } catch (IOException ex) {
                     throw new JmxException("Could not start JMX connector server after delay", ex);
                  }
               }
            };
            connectorThread.setName("JMX Connector Thread [" + this.serviceUrl + "]");
            connectorThread.setDaemon(this.daemon);
            connectorThread.start();
         } else {
            this.connectorServer.start();
         }

         if (this.logger.isInfoEnabled()) {
            this.logger.info("JMX connector server started: " + this.connectorServer);
         }
      } catch (IOException ex) {
         this.unregisterBeans();
         throw ex;
      }
   }

   public @Nullable JMXConnectorServer getObject() {
      return this.connectorServer;
   }

   public Class<? extends JMXConnectorServer> getObjectType() {
      return (Class<? extends JMXConnectorServer>)(this.connectorServer != null ? this.connectorServer.getClass() : JMXConnectorServer.class);
   }

   public boolean isSingleton() {
      return true;
   }

   public void destroy() throws IOException {
      try {
         if (this.connectorServer != null) {
            if (this.logger.isInfoEnabled()) {
               this.logger.info("Stopping JMX connector server: " + this.connectorServer);
            }

            this.connectorServer.stop();
         }
      } finally {
         this.unregisterBeans();
      }
   }
}
