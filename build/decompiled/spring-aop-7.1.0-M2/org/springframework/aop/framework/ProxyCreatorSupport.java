package org.springframework.aop.framework;

import java.util.ArrayList;
import java.util.List;
import org.springframework.util.Assert;

public class ProxyCreatorSupport extends AdvisedSupport {
   private AopProxyFactory aopProxyFactory;
   private final List<AdvisedSupportListener> listeners = new ArrayList<>();
   private boolean active = false;

   public ProxyCreatorSupport() {
      this.aopProxyFactory = DefaultAopProxyFactory.INSTANCE;
   }

   public ProxyCreatorSupport(AopProxyFactory aopProxyFactory) {
      Assert.notNull(aopProxyFactory, "AopProxyFactory must not be null");
      this.aopProxyFactory = aopProxyFactory;
   }

   public void setAopProxyFactory(AopProxyFactory aopProxyFactory) {
      Assert.notNull(aopProxyFactory, "AopProxyFactory must not be null");
      this.aopProxyFactory = aopProxyFactory;
   }

   public AopProxyFactory getAopProxyFactory() {
      return this.aopProxyFactory;
   }

   public void addListener(AdvisedSupportListener listener) {
      Assert.notNull(listener, "AdvisedSupportListener must not be null");
      this.listeners.add(listener);
   }

   public void removeListener(AdvisedSupportListener listener) {
      Assert.notNull(listener, "AdvisedSupportListener must not be null");
      this.listeners.remove(listener);
   }

   protected final synchronized AopProxy createAopProxy() {
      if (!this.active) {
         this.activate();
      }

      return this.getAopProxyFactory().createAopProxy(this);
   }

   private void activate() {
      this.active = true;

      for (AdvisedSupportListener listener : this.listeners) {
         listener.activated(this);
      }
   }

   @Override
   protected void adviceChanged() {
      super.adviceChanged();
      synchronized (this) {
         if (this.active) {
            for (AdvisedSupportListener listener : this.listeners) {
               listener.adviceChanged(this);
            }
         }
      }
   }

   protected final synchronized boolean isActive() {
      return this.active;
   }
}
