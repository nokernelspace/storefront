package org.springframework.objenesis.instantiator.android;

import java.io.ObjectStreamClass;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.springframework.objenesis.ObjenesisException;
import org.springframework.objenesis.instantiator.ObjectInstantiator;
import org.springframework.objenesis.instantiator.annotations.Instantiator;
import org.springframework.objenesis.instantiator.annotations.Typology;

@Instantiator(Typology.STANDARD)
public class Android18Instantiator<T> implements ObjectInstantiator<T> {
   private final Class<T> type;
   private final Method newInstanceMethod;
   private final Long objectConstructorId;

   public Android18Instantiator(Class<T> type) {
      this.type = type;
      this.newInstanceMethod = getNewInstanceMethod();
      this.objectConstructorId = findConstructorIdForJavaLangObjectConstructor();
   }

   @Override
   public T newInstance() {
      try {
         return this.type.cast(this.newInstanceMethod.invoke(null, this.type, this.objectConstructorId));
      } catch (Exception e) {
         throw new ObjenesisException(e);
      }
   }

   private static Method getNewInstanceMethod() {
      try {
         Method newInstanceMethod = ObjectStreamClass.class.getDeclaredMethod("newInstance", Class.class, long.class);
         newInstanceMethod.setAccessible(true);
         return newInstanceMethod;
      } catch (RuntimeException | NoSuchMethodException e) {
         throw new ObjenesisException(e);
      }
   }

   private static Long findConstructorIdForJavaLangObjectConstructor() {
      try {
         Method newInstanceMethod = ObjectStreamClass.class.getDeclaredMethod("getConstructorId", Class.class);
         newInstanceMethod.setAccessible(true);
         return (Long)newInstanceMethod.invoke(null, Object.class);
      } catch (RuntimeException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
         throw new ObjenesisException(e);
      }
   }
}
