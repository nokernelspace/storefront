package org.springframework.aop.aspectj.annotation;

import java.io.Serializable;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public class LazySingletonAspectInstanceFactoryDecorator implements MetadataAwareAspectInstanceFactory, Serializable {
   private final MetadataAwareAspectInstanceFactory maaif;
   private volatile @Nullable Object materialized;

   public LazySingletonAspectInstanceFactoryDecorator(MetadataAwareAspectInstanceFactory maaif) {
      Assert.notNull(maaif, "AspectInstanceFactory must not be null");
      this.maaif = maaif;
   }

   @Override
   public Object getAspectInstance() {
      Object aspectInstance = this.materialized;
      if (aspectInstance == null) {
         Object mutex = this.maaif.getAspectCreationMutex();
         if (mutex == null) {
            aspectInstance = this.maaif.getAspectInstance();
            this.materialized = aspectInstance;
         } else {
            synchronized (mutex) {
               aspectInstance = this.materialized;
               if (aspectInstance == null) {
                  aspectInstance = this.maaif.getAspectInstance();
                  this.materialized = aspectInstance;
               }
            }
         }
      }

      return aspectInstance;
   }

   public boolean isMaterialized() {
      return this.materialized != null;
   }

   @Override
   public @Nullable ClassLoader getAspectClassLoader() {
      return this.maaif.getAspectClassLoader();
   }

   @Override
   public AspectMetadata getAspectMetadata() {
      return this.maaif.getAspectMetadata();
   }

   @Override
   public @Nullable Object getAspectCreationMutex() {
      return this.maaif.getAspectCreationMutex();
   }

   public int getOrder() {
      return this.maaif.getOrder();
   }

   @Override
   public String toString() {
      return "LazySingletonAspectInstanceFactoryDecorator: decorating " + this.maaif;
   }
}
