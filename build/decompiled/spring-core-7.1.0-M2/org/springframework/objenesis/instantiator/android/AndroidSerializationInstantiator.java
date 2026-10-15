package org.springframework.objenesis.instantiator.android;

import java.io.ObjectStreamClass;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.springframework.objenesis.ObjenesisException;
import org.springframework.objenesis.instantiator.ObjectInstantiator;
import org.springframework.objenesis.instantiator.annotations.Instantiator;
import org.springframework.objenesis.instantiator.annotations.Typology;

@Instantiator(Typology.SERIALIZATION)
public class AndroidSerializationInstantiator<T> implements ObjectInstantiator<T> {
   private final Class<T> type;
   private final ObjectStreamClass objectStreamClass;
   private final Method newInstanceMethod;

   public AndroidSerializationInstantiator(Class<T> type) {
      this.type = type;
      this.newInstanceMethod = getNewInstanceMethod();

      Method m;
      try {
         m = ObjectStreamClass.class.getMethod("lookupAny", Class.class);
      } catch (NoSuchMethodException e) {
         throw new ObjenesisException(e);
      }

      try {
         this.objectStreamClass = (ObjectStreamClass)m.invoke(null, type);
      } catch (IllegalAccessException | InvocationTargetException e) {
         throw new ObjenesisException(e);
      }
   }

   @Override
   public T newInstance() {
      try {
         return this.type.cast(this.newInstanceMethod.invoke(this.objectStreamClass, this.type));
      } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
         throw new ObjenesisException(e);
      }
   }

   private static Method getNewInstanceMethod() {
      try {
         Method newInstanceMethod = ObjectStreamClass.class.getDeclaredMethod("newInstance", Class.class);
         newInstanceMethod.setAccessible(true);
         return newInstanceMethod;
      } catch (RuntimeException | NoSuchMethodException e) {
         throw new ObjenesisException(e);
      }
   }
}
