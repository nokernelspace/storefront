package org.springframework.objenesis.strategy;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import org.springframework.objenesis.ObjenesisException;
import org.springframework.objenesis.instantiator.ObjectInstantiator;

public class SingleInstantiatorStrategy implements InstantiatorStrategy {
   private final Constructor<?> constructor;

   public <T extends ObjectInstantiator<?>> SingleInstantiatorStrategy(Class<T> instantiator) {
      try {
         this.constructor = instantiator.getConstructor(Class.class);
      } catch (NoSuchMethodException e) {
         throw new ObjenesisException(e);
      }
   }

   @Override
   public <T> ObjectInstantiator<T> newInstantiatorOf(Class<T> type) {
      try {
         return (ObjectInstantiator<T>)this.constructor.newInstance(type);
      } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
         throw new ObjenesisException(e);
      }
   }
}
