package org.springframework.jmx.support;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import javax.management.MBeanServerConnection;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.target.AbstractLazyCreationTargetSource;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

public class MBeanServerConnectionFactoryBean implements FactoryBean<MBeanServerConnection>, BeanClassLoaderAware, InitializingBean, DisposableBean {
   private @Nullable JMXServiceURL serviceUrl;
   private final Map<String, Object> environment = new HashMap<>();
   private boolean connectOnStartup = true;
   private @Nullable ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();
   private @Nullable JMXConnector connector;
   private @Nullable MBeanServerConnection connection;
   private MBeanServerConnectionFactoryBean.@Nullable JMXConnectorLazyInitTargetSource connectorTargetSource;

   public void setServiceUrl(String url) throws MalformedURLException {
      this.serviceUrl = new JMXServiceURL(url);
   }

   public void setEnvironment(Properties environment) {
      CollectionUtils.mergePropertiesIntoMap(environment, this.environment);
   }

   public void setEnvironmentMap(@Nullable Map<String, ?> environment) {
      if (environment != null) {
         this.environment.putAll((Map<? extends String, ? extends Object>)environment);
      }
   }

   public void setConnectOnStartup(boolean connectOnStartup) {
      this.connectOnStartup = connectOnStartup;
   }

   public void setBeanClassLoader(ClassLoader classLoader) {
      this.beanClassLoader = classLoader;
   }

   public void afterPropertiesSet() throws IOException {
      if (this.serviceUrl == null) {
         throw new IllegalArgumentException("Property 'serviceUrl' is required");
      }

      if (this.connectOnStartup) {
         this.connect();
      } else {
         this.createLazyConnection();
      }
   }

   private void connect() throws IOException {
      Assert.state(this.serviceUrl != null, "No JMXServiceURL set");
      this.connector = JMXConnectorFactory.connect(this.serviceUrl, this.environment);
      this.connection = this.connector.getMBeanServerConnection();
   }

   private void createLazyConnection() {
      this.connectorTargetSource = new MBeanServerConnectionFactoryBean.JMXConnectorLazyInitTargetSource();
      TargetSource connectionTargetSource = new MBeanServerConnectionFactoryBean.MBeanServerConnectionLazyInitTargetSource();
      this.connector = (JMXConnector)new ProxyFactory(JMXConnector.class, this.connectorTargetSource).getProxy(this.beanClassLoader);
      this.connection = (MBeanServerConnection)new ProxyFactory(MBeanServerConnection.class, connectionTargetSource).getProxy(this.beanClassLoader);
   }

   public @Nullable MBeanServerConnection getObject() {
      return this.connection;
   }

   public Class<? extends MBeanServerConnection> getObjectType() {
      return (Class<? extends MBeanServerConnection>)(this.connection != null ? this.connection.getClass() : MBeanServerConnection.class);
   }

   public boolean isSingleton() {
      return true;
   }

   public void destroy() throws IOException {
      if (this.connector != null && (this.connectorTargetSource == null || this.connectorTargetSource.isInitialized())) {
         this.connector.close();
      }
   }

   private class JMXConnectorLazyInitTargetSource extends AbstractLazyCreationTargetSource {
      protected Object createObject() throws Exception {
         Assert.state(MBeanServerConnectionFactoryBean.this.serviceUrl != null, "No JMXServiceURL set");
         return JMXConnectorFactory.connect(MBeanServerConnectionFactoryBean.this.serviceUrl, MBeanServerConnectionFactoryBean.this.environment);
      }

      public Class<?> getTargetClass() {
         return JMXConnector.class;
      }
   }

   private class MBeanServerConnectionLazyInitTargetSource extends AbstractLazyCreationTargetSource {
      protected Object createObject() throws Exception {
         Assert.state(MBeanServerConnectionFactoryBean.this.connector != null, "JMXConnector not initialized");
         return MBeanServerConnectionFactoryBean.this.connector.getMBeanServerConnection();
      }

      public Class<?> getTargetClass() {
         return MBeanServerConnection.class;
      }
   }
}
