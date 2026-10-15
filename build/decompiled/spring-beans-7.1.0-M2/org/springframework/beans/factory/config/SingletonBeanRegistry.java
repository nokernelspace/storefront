package org.springframework.beans.factory.config;

import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;

public interface SingletonBeanRegistry {
   void registerSingleton(String beanName, Object singletonObject);

   void addSingletonCallback(String beanName, Consumer<Object> singletonConsumer);

   @Nullable Object getSingleton(String beanName);

   boolean containsSingleton(String beanName);

   String[] getSingletonNames();

   int getSingletonCount();

   @Deprecated(since = "6.2")
   Object getSingletonMutex();
}
