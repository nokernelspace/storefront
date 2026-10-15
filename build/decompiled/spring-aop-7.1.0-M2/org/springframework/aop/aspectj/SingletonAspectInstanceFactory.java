package org.springframework.aop.aspectj;

import java.io.Serializable;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.util.Assert;

public class SingletonAspectInstanceFactory implements AspectInstanceFactory, Serializable {
   private final Object aspectInstance;

   public SingletonAspectInstanceFactory(Object aspectInstance) {
      Assert.notNull(aspectInstance, "Aspect instance must not be null");
      this.aspectInstance = aspectInstance;
   }

   @Override
   public final Object getAspectInstance() {
      return this.aspectInstance;
   }

   @Override
   public @Nullable ClassLoader getAspectClassLoader() {
      return this.aspectInstance.getClass().getClassLoader();
   }

   public int getOrder() {
      return this.aspectInstance instanceof Ordered ordered ? ordered.getOrder() : this.getOrderForAspectClass(this.aspectInstance.getClass());
   }

   protected int getOrderForAspectClass(Class<?> aspectClass) {
      return Integer.MAX_VALUE;
   }
}
