package org.springframework.objenesis.strategy;

import org.springframework.objenesis.instantiator.ObjectInstantiator;
import org.springframework.objenesis.instantiator.android.Android10Instantiator;
import org.springframework.objenesis.instantiator.android.Android17Instantiator;
import org.springframework.objenesis.instantiator.android.Android18Instantiator;
import org.springframework.objenesis.instantiator.gcj.GCJInstantiator;
import org.springframework.objenesis.instantiator.perc.PercInstantiator;
import org.springframework.objenesis.instantiator.sun.SunReflectionFactoryInstantiator;
import org.springframework.objenesis.instantiator.sun.UnsafeFactoryInstantiator;

public class StdInstantiatorStrategy extends BaseInstantiatorStrategy {
   @Override
   public <T> ObjectInstantiator<T> newInstantiatorOf(Class<T> type) {
      if (PlatformDescription.isThisJVM("Java HotSpot") || PlatformDescription.isThisJVM("OpenJDK")) {
         return new SunReflectionFactoryInstantiator<>(type);
      }

      if (PlatformDescription.isThisJVM("Dalvik")) {
         if (PlatformDescription.isAndroidOpenJDK()) {
            return new UnsafeFactoryInstantiator<>(type);
         } else if (PlatformDescription.ANDROID_VERSION <= 10) {
            return new Android10Instantiator<>(type);
         } else {
            return PlatformDescription.ANDROID_VERSION <= 17 ? new Android17Instantiator<>(type) : new Android18Instantiator<>(type);
         }
      } else if (PlatformDescription.isThisJVM("GNU libgcj")) {
         return new GCJInstantiator<>(type);
      } else {
         return PlatformDescription.isThisJVM("PERC") ? new PercInstantiator<>(type) : new UnsafeFactoryInstantiator<>(type);
      }
   }
}
