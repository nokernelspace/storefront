package org.springframework.aop.aspectj.annotation;

import org.springframework.aop.aspectj.SimpleAspectInstanceFactory;
import org.springframework.core.annotation.OrderUtils;

public class SimpleMetadataAwareAspectInstanceFactory extends SimpleAspectInstanceFactory implements MetadataAwareAspectInstanceFactory {
   private final AspectMetadata metadata;

   public SimpleMetadataAwareAspectInstanceFactory(Class<?> aspectClass, String aspectName) {
      super(aspectClass);
      this.metadata = new AspectMetadata(aspectClass, aspectName);
   }

   @Override
   public final AspectMetadata getAspectMetadata() {
      return this.metadata;
   }

   @Override
   public Object getAspectCreationMutex() {
      return this;
   }

   @Override
   protected int getOrderForAspectClass(Class<?> aspectClass) {
      return OrderUtils.getOrder(aspectClass, Integer.MAX_VALUE);
   }
}
