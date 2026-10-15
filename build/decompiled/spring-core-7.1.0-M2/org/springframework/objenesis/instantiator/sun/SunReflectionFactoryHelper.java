package org.springframework.objenesis.instantiator.sun;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.springframework.objenesis.ObjenesisException;

class SunReflectionFactoryHelper {
   public static <T> Constructor<T> newConstructorForSerialization(Class<T> type, Constructor<?> constructor) {
      Class<?> reflectionFactoryClass = getReflectionFactoryClass();
      Object reflectionFactory = createReflectionFactory(reflectionFactoryClass);
      Method newConstructorForSerializationMethod = getNewConstructorForSerializationMethod(reflectionFactoryClass);

      try {
         return (Constructor<T>)newConstructorForSerializationMethod.invoke(reflectionFactory, type, constructor);
      } catch (IllegalArgumentException | IllegalAccessException | InvocationTargetException e) {
         throw new ObjenesisException(e);
      }
   }

   private static Class<?> getReflectionFactoryClass() {
      try {
         return Class.forName("sun.reflect.ReflectionFactory");
      } catch (ClassNotFoundException e) {
         try {
            return Class.forName("jdk.internal.reflect.ReflectionFactory");
         } catch (ClassNotFoundException ex) {
            throw new ObjenesisException("You might need to add the unsupported module with --add-modules jdk.unsupported", e);
         }
      }
   }

   private static Object createReflectionFactory(Class<?> reflectionFactoryClass) {
      try {
         Method method = reflectionFactoryClass.getDeclaredMethod("getReflectionFactory");
         return method.invoke(null);
      } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | IllegalArgumentException e) {
         throw new ObjenesisException(e);
      }
   }

   private static Method getNewConstructorForSerializationMethod(Class<?> reflectionFactoryClass) {
      try {
         return reflectionFactoryClass.getDeclaredMethod("newConstructorForSerialization", Class.class, Constructor.class);
      } catch (NoSuchMethodException e) {
         throw new ObjenesisException(e);
      }
   }
}
