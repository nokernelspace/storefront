package org.springframework.context.event;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.ResolvableType;
import org.springframework.util.Assert;
import org.springframework.util.ConcurrentReferenceHashMap;

public class GenericApplicationListenerAdapter implements GenericApplicationListener {
   private static final Map<Class<?>, ResolvableType> eventTypeCache = new ConcurrentReferenceHashMap();
   private final ApplicationListener<ApplicationEvent> delegate;
   private final @Nullable ResolvableType declaredEventType;

   public GenericApplicationListenerAdapter(ApplicationListener<?> delegate) {
      Assert.notNull(delegate, "Delegate listener must not be null");
      this.delegate = (ApplicationListener<ApplicationEvent>)delegate;
      this.declaredEventType = resolveDeclaredEventType(this.delegate);
   }

   @Override
   public void onApplicationEvent(ApplicationEvent event) {
      this.delegate.onApplicationEvent(event);
   }

   @Override
   public boolean supportsEventType(ResolvableType eventType) {
      if (this.delegate instanceof GenericApplicationListener gal) {
         return gal.supportsEventType(eventType);
      } else if (this.delegate instanceof SmartApplicationListener sal) {
         Class<? extends ApplicationEvent> eventClass = eventType.resolve();
         return eventClass != null && sal.supportsEventType(eventClass);
      } else {
         return this.declaredEventType == null || this.declaredEventType.isAssignableFrom(eventType);
      }
   }

   @Override
   public boolean supportsSourceType(@Nullable Class<?> sourceType) {
      return !(this.delegate instanceof SmartApplicationListener sal && !sal.supportsSourceType(sourceType));
   }

   @Override
   public int getOrder() {
      return this.delegate instanceof Ordered ordered ? ordered.getOrder() : Integer.MAX_VALUE;
   }

   @Override
   public String getListenerId() {
      return this.delegate instanceof SmartApplicationListener sal ? sal.getListenerId() : "";
   }

   private static @Nullable ResolvableType resolveDeclaredEventType(ApplicationListener<ApplicationEvent> listener) {
      ResolvableType declaredEventType = resolveDeclaredEventType(listener.getClass());
      if (declaredEventType == null || declaredEventType.isAssignableFrom(ApplicationEvent.class)) {
         Class<?> targetClass = AopUtils.getTargetClass(listener);
         if (targetClass != listener.getClass()) {
            declaredEventType = resolveDeclaredEventType(targetClass);
         }
      }

      return declaredEventType;
   }

   static @Nullable ResolvableType resolveDeclaredEventType(Class<?> listenerType) {
      ResolvableType eventType = eventTypeCache.get(listenerType);
      if (eventType == null) {
         eventType = ResolvableType.forClass(listenerType).as(ApplicationListener.class).getGeneric(new int[0]);
         eventTypeCache.put(listenerType, eventType);
      }

      return eventType != ResolvableType.NONE ? eventType : null;
   }
}
