package org.springframework.beans.factory.config;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.FactoryBeanNotInitializedException;

public class MethodInvokingFactoryBean extends MethodInvokingBean implements FactoryBean<Object> {
   private boolean singleton = true;
   private boolean initialized = false;
   private @Nullable Object singletonObject;

   public void setSingleton(boolean singleton) {
      this.singleton = singleton;
   }

   @Override
   public void afterPropertiesSet() throws Exception {
      this.prepare();
      if (this.singleton) {
         this.initialized = true;
         this.singletonObject = this.invokeWithTargetException();
      }
   }

   @Override
   public @Nullable Object getObject() throws Exception {
      if (this.singleton) {
         if (!this.initialized) {
            throw new FactoryBeanNotInitializedException();
         } else {
            return this.singletonObject;
         }
      } else {
         return this.invokeWithTargetException();
      }
   }

   @Override
   public @Nullable Class<?> getObjectType() {
      return !this.isPrepared() ? null : this.getPreparedMethod().getReturnType();
   }

   @Override
   public boolean isSingleton() {
      return this.singleton;
   }
}
