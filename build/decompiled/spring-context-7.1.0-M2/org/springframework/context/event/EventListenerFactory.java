package org.springframework.context.event;

import java.lang.reflect.Method;
import org.springframework.context.ApplicationListener;

public interface EventListenerFactory {
   boolean supportsMethod(Method method);

   ApplicationListener<?> createApplicationListener(String beanName, Class<?> type, Method method);
}
