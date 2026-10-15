package org.springframework.objenesis.strategy;

import java.io.NotSerializableException;
import java.io.Serializable;
import org.springframework.objenesis.ObjenesisException;
import org.springframework.objenesis.instantiator.ObjectInstantiator;
import org.springframework.objenesis.instantiator.android.AndroidSerializationInstantiator;
import org.springframework.objenesis.instantiator.basic.ObjectStreamClassInstantiator;
import org.springframework.objenesis.instantiator.gcj.GCJSerializationInstantiator;
import org.springframework.objenesis.instantiator.perc.PercSerializationInstantiator;
import org.springframework.objenesis.instantiator.sun.SunReflectionFactorySerializationInstantiator;

public class SerializingInstantiatorStrategy extends BaseInstantiatorStrategy {
   @Override
   public <T> ObjectInstantiator<T> newInstantiatorOf(Class<T> type) {
      if (!Serializable.class.isAssignableFrom(type)) {
         throw new ObjenesisException(new NotSerializableException(type + " not serializable"));
      } else if (PlatformDescription.JVM_NAME.startsWith("Java HotSpot") || PlatformDescription.isThisJVM("OpenJDK")) {
         return new SunReflectionFactorySerializationInstantiator<>(type);
      } else if (PlatformDescription.JVM_NAME.startsWith("Dalvik")) {
         return PlatformDescription.isAndroidOpenJDK() ? new ObjectStreamClassInstantiator<>(type) : new AndroidSerializationInstantiator<>(type);
      } else if (PlatformDescription.JVM_NAME.startsWith("GNU libgcj")) {
         return new GCJSerializationInstantiator<>(type);
      } else {
         return PlatformDescription.JVM_NAME.startsWith("PERC")
            ? new PercSerializationInstantiator<>(type)
            : new SunReflectionFactorySerializationInstantiator<>(type);
      }
   }
}
