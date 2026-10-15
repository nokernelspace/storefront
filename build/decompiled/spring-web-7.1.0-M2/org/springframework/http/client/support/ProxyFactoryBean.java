package org.springframework.http.client.support;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketAddress;
import java.net.Proxy.Type;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;

public class ProxyFactoryBean implements FactoryBean<Proxy>, InitializingBean {
   private Type type = Type.HTTP;
   private @Nullable String hostname;
   private int port = -1;
   private @Nullable Proxy proxy;

   public void setType(Type type) {
      this.type = type;
   }

   public void setHostname(String hostname) {
      this.hostname = hostname;
   }

   public void setPort(int port) {
      this.port = port;
   }

   public void afterPropertiesSet() throws IllegalArgumentException {
      Assert.notNull(this.type, "Property 'type' is required");
      Assert.notNull(this.hostname, "Property 'hostname' is required");
      if (this.port >= 0 && this.port <= 65535) {
         SocketAddress socketAddress = new InetSocketAddress(this.hostname, this.port);
         this.proxy = new Proxy(this.type, socketAddress);
      } else {
         throw new IllegalArgumentException("Property 'port' value out of range: " + this.port);
      }
   }

   public @Nullable Proxy getObject() {
      return this.proxy;
   }

   public Class<?> getObjectType() {
      return Proxy.class;
   }

   public boolean isSingleton() {
      return true;
   }
}
