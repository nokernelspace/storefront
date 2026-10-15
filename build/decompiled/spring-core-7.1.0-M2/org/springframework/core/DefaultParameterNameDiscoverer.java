package org.springframework.core;

import org.jspecify.annotations.Nullable;

public class DefaultParameterNameDiscoverer extends PrioritizedParameterNameDiscoverer {
   private static final boolean KOTLIN_REFLECT_PRESENT = KotlinDetector.isKotlinReflectPresent();
   private static volatile @Nullable DefaultParameterNameDiscoverer sharedInstance;

   public DefaultParameterNameDiscoverer() {
      if (KOTLIN_REFLECT_PRESENT) {
         this.addDiscoverer(new KotlinReflectionParameterNameDiscoverer());
      }

      this.addDiscoverer(new StandardReflectionParameterNameDiscoverer());
   }

   public static ParameterNameDiscoverer getSharedInstance() {
      DefaultParameterNameDiscoverer pnd = sharedInstance;
      if (pnd == null) {
         synchronized (DefaultParameterNameDiscoverer.class) {
            pnd = sharedInstance;
            if (pnd == null) {
               pnd = new DefaultParameterNameDiscoverer();
               sharedInstance = pnd;
            }
         }
      }

      return pnd;
   }
}
