package org.springframework.objenesis.instantiator.sun;

import java.io.NotSerializableException;
import java.lang.reflect.Constructor;
import org.springframework.objenesis.ObjenesisException;
import org.springframework.objenesis.instantiator.ObjectInstantiator;
import org.springframework.objenesis.instantiator.SerializationInstantiatorHelper;
import org.springframework.objenesis.instantiator.annotations.Instantiator;
import org.springframework.objenesis.instantiator.annotations.Typology;

@Instantiator(Typology.SERIALIZATION)
public class SunReflectionFactorySerializationInstantiator<T> implements ObjectInstantiator<T> {
   private final Constructor<T> mungedConstructor;

   public SunReflectionFactorySerializationInstantiator(Class<T> type) {
      Class<? super T> nonSerializableAncestor = SerializationInstantiatorHelper.getNonSerializableSuperClass(type);

      Constructor<? super T> nonSerializableAncestorConstructor;
      try {
         nonSerializableAncestorConstructor = nonSerializableAncestor.getDeclaredConstructor((Class<?>[])null);
      } catch (NoSuchMethodException e) {
         throw new ObjenesisException(new NotSerializableException(type + " has no suitable superclass constructor"));
      }

      this.mungedConstructor = SunReflectionFactoryHelper.newConstructorForSerialization(type, nonSerializableAncestorConstructor);

      try {
         this.mungedConstructor.setAccessible(true);
      } catch (RuntimeException e) {
         if (e.getClass().getName().equals("java.lang.reflect.InaccessibleObjectException")) {
            throw new ObjenesisException("Unable to access constructor, try adding --add-opens yyy/zzz=org.objenesis", e);
         } else {
            throw e;
         }
      }
   }

   @Override
   public T newInstance() {
      try {
         return this.mungedConstructor.newInstance((Object[])null);
      } catch (Exception e) {
         throw new ObjenesisException(e);
      }
   }
}
