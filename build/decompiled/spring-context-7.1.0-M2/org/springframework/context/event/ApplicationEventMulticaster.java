package org.springframework.context.event;

import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.ResolvableType;

public interface ApplicationEventMulticaster {
   void addApplicationListener(ApplicationListener<?> listener);

   void addApplicationListenerBean(String listenerBeanName);

   void removeApplicationListener(ApplicationListener<?> listener);

   void removeApplicationListenerBean(String listenerBeanName);

   void removeApplicationListeners(Predicate<ApplicationListener<?>> predicate);

   void removeApplicationListenerBeans(Predicate<String> predicate);

   void removeAllListeners();

   void multicastEvent(ApplicationEvent event);

   void multicastEvent(ApplicationEvent event, @Nullable ResolvableType eventType);
}
