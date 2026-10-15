package org.springframework.beans.factory.config;

import java.util.Properties;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.CollectionFactory;

public class YamlPropertiesFactoryBean extends YamlProcessor implements FactoryBean<Properties>, InitializingBean {
   private boolean singleton = true;
   private @Nullable Properties properties;

   public void setSingleton(boolean singleton) {
      this.singleton = singleton;
   }

   @Override
   public boolean isSingleton() {
      return this.singleton;
   }

   @Override
   public void afterPropertiesSet() {
      if (this.isSingleton()) {
         this.properties = this.createProperties();
      }
   }

   public @Nullable Properties getObject() {
      return this.properties != null ? this.properties : this.createProperties();
   }

   @Override
   public Class<?> getObjectType() {
      return Properties.class;
   }

   protected Properties createProperties() {
      Properties result = CollectionFactory.createStringAdaptingProperties();
      this.process((properties, map) -> result.putAll(properties));
      return result;
   }
}
