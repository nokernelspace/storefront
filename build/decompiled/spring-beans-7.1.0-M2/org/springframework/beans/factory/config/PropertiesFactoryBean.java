package org.springframework.beans.factory.config;

import java.io.IOException;
import java.util.Properties;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.support.PropertiesLoaderSupport;

public class PropertiesFactoryBean extends PropertiesLoaderSupport implements FactoryBean<Properties>, InitializingBean {
   private boolean singleton = true;
   private @Nullable Properties singletonInstance;

   public final void setSingleton(boolean singleton) {
      this.singleton = singleton;
   }

   @Override
   public final boolean isSingleton() {
      return this.singleton;
   }

   @Override
   public final void afterPropertiesSet() throws IOException {
      if (this.singleton) {
         this.singletonInstance = this.createProperties();
      }
   }

   public final @Nullable Properties getObject() throws IOException {
      return this.singleton ? this.singletonInstance : this.createProperties();
   }

   @Override
   public Class<Properties> getObjectType() {
      return Properties.class;
   }

   protected Properties createProperties() throws IOException {
      return this.mergeProperties();
   }
}
