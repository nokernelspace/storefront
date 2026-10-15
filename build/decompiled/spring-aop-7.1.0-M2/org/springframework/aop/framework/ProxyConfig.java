package org.springframework.aop.framework;

import java.io.Serializable;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public class ProxyConfig implements Serializable {
   private static final long serialVersionUID = -8409359707199703185L;
   private @Nullable Boolean proxyTargetClass;
   private @Nullable Boolean optimize;
   private @Nullable Boolean opaque;
   private @Nullable Boolean exposeProxy;
   private @Nullable Boolean frozen;

   public void setProxyTargetClass(boolean proxyTargetClass) {
      this.proxyTargetClass = proxyTargetClass;
   }

   public boolean isProxyTargetClass() {
      return this.proxyTargetClass != null && this.proxyTargetClass;
   }

   public void setOptimize(boolean optimize) {
      this.optimize = optimize;
   }

   public boolean isOptimize() {
      return this.optimize != null && this.optimize;
   }

   public void setOpaque(boolean opaque) {
      this.opaque = opaque;
   }

   public boolean isOpaque() {
      return this.opaque != null && this.opaque;
   }

   public void setExposeProxy(boolean exposeProxy) {
      this.exposeProxy = exposeProxy;
   }

   public boolean isExposeProxy() {
      return this.exposeProxy != null && this.exposeProxy;
   }

   public void setFrozen(boolean frozen) {
      this.frozen = frozen;
   }

   public boolean isFrozen() {
      return this.frozen != null && this.frozen;
   }

   public void copyFrom(ProxyConfig other) {
      Assert.notNull(other, "Other ProxyConfig object must not be null");
      this.proxyTargetClass = other.proxyTargetClass;
      this.optimize = other.optimize;
      this.opaque = other.opaque;
      this.exposeProxy = other.exposeProxy;
      this.frozen = other.frozen;
   }

   public void copyDefault(ProxyConfig other) {
      Assert.notNull(other, "Other ProxyConfig object must not be null");
      if (this.proxyTargetClass == null) {
         this.proxyTargetClass = other.proxyTargetClass;
      }

      if (this.optimize == null) {
         this.optimize = other.optimize;
      }

      if (this.opaque == null) {
         this.opaque = other.opaque;
      }

      if (this.exposeProxy == null) {
         this.exposeProxy = other.exposeProxy;
      }

      if (this.frozen == null) {
         this.frozen = other.frozen;
      }
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append("proxyTargetClass=").append(this.proxyTargetClass).append("; ");
      sb.append("optimize=").append(this.optimize).append("; ");
      sb.append("opaque=").append(this.opaque).append("; ");
      sb.append("exposeProxy=").append(this.exposeProxy).append("; ");
      sb.append("frozen=").append(this.frozen);
      return sb.toString();
   }
}
