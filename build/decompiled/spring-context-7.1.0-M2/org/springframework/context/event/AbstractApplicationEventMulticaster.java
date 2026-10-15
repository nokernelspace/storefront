package org.springframework.context.event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.ResolvableType;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

public abstract class AbstractApplicationEventMulticaster implements ApplicationEventMulticaster, BeanClassLoaderAware, BeanFactoryAware {
   private final AbstractApplicationEventMulticaster.DefaultListenerRetriever defaultRetriever = new AbstractApplicationEventMulticaster.DefaultListenerRetriever(
      
   );
   final Map<AbstractApplicationEventMulticaster.ListenerCacheKey, AbstractApplicationEventMulticaster.CachedListenerRetriever> retrieverCache = new ConcurrentHashMap<>(
      64
   );
   private @Nullable ClassLoader beanClassLoader;
   private @Nullable ConfigurableBeanFactory beanFactory;

   public void setBeanClassLoader(ClassLoader classLoader) {
      this.beanClassLoader = classLoader;
   }

   public void setBeanFactory(BeanFactory beanFactory) {
      if (beanFactory instanceof ConfigurableBeanFactory cbf) {
         this.beanFactory = cbf;
         if (this.beanClassLoader == null) {
            this.beanClassLoader = this.beanFactory.getBeanClassLoader();
         }
      } else {
         throw new IllegalStateException("Not running in a ConfigurableBeanFactory: " + beanFactory);
      }
   }

   private ConfigurableBeanFactory getBeanFactory() {
      if (this.beanFactory == null) {
         throw new IllegalStateException("ApplicationEventMulticaster cannot retrieve listener beans because it is not associated with a BeanFactory");
      } else {
         return this.beanFactory;
      }
   }

   @Override
   public void addApplicationListener(ApplicationListener<?> listener) {
      synchronized (this.defaultRetriever) {
         Object singletonTarget = AopProxyUtils.ultimateSingletonTarget(listener);
         if (singletonTarget != listener && singletonTarget instanceof ApplicationListener) {
            this.defaultRetriever.applicationListeners.remove(singletonTarget);
         }

         this.defaultRetriever.applicationListeners.add(listener);
         this.retrieverCache.clear();
      }
   }

   @Override
   public void addApplicationListenerBean(String listenerBeanName) {
      synchronized (this.defaultRetriever) {
         this.defaultRetriever.applicationListenerBeans.add(listenerBeanName);
         this.retrieverCache.clear();
      }
   }

   @Override
   public void removeApplicationListener(ApplicationListener<?> listener) {
      synchronized (this.defaultRetriever) {
         this.defaultRetriever.applicationListeners.remove(listener);
         this.retrieverCache.clear();
      }
   }

   @Override
   public void removeApplicationListenerBean(String listenerBeanName) {
      synchronized (this.defaultRetriever) {
         this.defaultRetriever.applicationListenerBeans.remove(listenerBeanName);
         this.retrieverCache.clear();
      }
   }

   @Override
   public void removeApplicationListeners(Predicate<ApplicationListener<?>> predicate) {
      synchronized (this.defaultRetriever) {
         this.defaultRetriever.applicationListeners.removeIf(predicate);
         this.retrieverCache.clear();
      }
   }

   @Override
   public void removeApplicationListenerBeans(Predicate<String> predicate) {
      synchronized (this.defaultRetriever) {
         this.defaultRetriever.applicationListenerBeans.removeIf(predicate);
         this.retrieverCache.clear();
      }
   }

   @Override
   public void removeAllListeners() {
      synchronized (this.defaultRetriever) {
         this.defaultRetriever.applicationListeners.clear();
         this.defaultRetriever.applicationListenerBeans.clear();
         this.retrieverCache.clear();
      }
   }

   protected Collection<ApplicationListener<?>> getApplicationListeners() {
      synchronized (this.defaultRetriever) {
         return this.defaultRetriever.getApplicationListeners();
      }
   }

   protected Collection<ApplicationListener<?>> getApplicationListeners(ApplicationEvent event, ResolvableType eventType) {
      Object source = event.getSource();
      Class<?> sourceType = source != null ? source.getClass() : null;
      AbstractApplicationEventMulticaster.ListenerCacheKey cacheKey = new AbstractApplicationEventMulticaster.ListenerCacheKey(eventType, sourceType);
      AbstractApplicationEventMulticaster.CachedListenerRetriever newRetriever = null;
      AbstractApplicationEventMulticaster.CachedListenerRetriever existingRetriever = this.retrieverCache.get(cacheKey);
      if (existingRetriever == null
         && (
            this.beanClassLoader == null
               || ClassUtils.isCacheSafe(event.getClass(), this.beanClassLoader)
                  && (sourceType == null || ClassUtils.isCacheSafe(sourceType, this.beanClassLoader))
         )) {
         newRetriever = new AbstractApplicationEventMulticaster.CachedListenerRetriever();
         existingRetriever = this.retrieverCache.putIfAbsent(cacheKey, newRetriever);
         if (existingRetriever != null) {
            newRetriever = null;
         }
      }

      if (existingRetriever != null) {
         Collection<ApplicationListener<?>> result = existingRetriever.getApplicationListeners();
         if (result != null) {
            return result;
         }
      }

      return this.retrieveApplicationListeners(eventType, sourceType, newRetriever);
   }

   private Collection<ApplicationListener<?>> retrieveApplicationListeners(
      ResolvableType eventType, @Nullable Class<?> sourceType, AbstractApplicationEventMulticaster.@Nullable CachedListenerRetriever retriever
   ) {
      List<ApplicationListener<?>> allListeners = new ArrayList<>();
      Set<ApplicationListener<?>> filteredListeners = retriever != null ? new LinkedHashSet<>() : null;
      Set<String> filteredListenerBeans = retriever != null ? new LinkedHashSet<>() : null;
      Set<ApplicationListener<?>> listeners;
      Set<String> listenerBeans;
      synchronized (this.defaultRetriever) {
         listeners = new LinkedHashSet<>(this.defaultRetriever.applicationListeners);
         listenerBeans = new LinkedHashSet<>(this.defaultRetriever.applicationListenerBeans);
      }

      for (ApplicationListener<?> listener : listeners) {
         if (this.supportsEvent(listener, eventType, sourceType)) {
            if (retriever != null) {
               filteredListeners.add(listener);
            }

            allListeners.add(listener);
         }
      }

      if (!listenerBeans.isEmpty()) {
         ConfigurableBeanFactory beanFactory = this.getBeanFactory();

         for (String listenerBeanName : listenerBeans) {
            try {
               if (this.supportsEvent(beanFactory, listenerBeanName, eventType)) {
                  ApplicationListener<?> listener = (ApplicationListener<?>)beanFactory.getBean(listenerBeanName, ApplicationListener.class);
                  ApplicationListener<?> unwrappedListener = (ApplicationListener<?>)AopProxyUtils.ultimateSingletonTarget(listener);
                  if (listener != unwrappedListener) {
                     if (filteredListeners != null && filteredListeners.contains(unwrappedListener)) {
                        filteredListeners.remove(unwrappedListener);
                        filteredListeners.add(listener);
                     }

                     if (allListeners.contains(unwrappedListener)) {
                        allListeners.remove(unwrappedListener);
                        allListeners.add(listener);
                     }
                  }

                  if (!allListeners.contains(listener) && this.supportsEvent(listener, eventType, sourceType)) {
                     if (retriever != null) {
                        if (beanFactory.isSingleton(listenerBeanName)) {
                           filteredListeners.add(listener);
                        } else {
                           filteredListenerBeans.add(listenerBeanName);
                        }
                     }

                     allListeners.add(listener);
                  }
               } else {
                  Object listener = beanFactory.getSingleton(listenerBeanName);
                  if (retriever != null) {
                     filteredListeners.remove(listener);
                  }

                  allListeners.remove(listener);
               }
            } catch (NoSuchBeanDefinitionException var14) {
            }
         }
      }

      AnnotationAwareOrderComparator.sort(allListeners);
      if (retriever != null) {
         if (CollectionUtils.isEmpty(filteredListenerBeans)) {
            retriever.applicationListeners = new LinkedHashSet<>(allListeners);
            retriever.applicationListenerBeans = filteredListenerBeans;
         } else {
            retriever.applicationListeners = filteredListeners;
            retriever.applicationListenerBeans = filteredListenerBeans;
         }
      }

      return allListeners;
   }

   private boolean supportsEvent(ConfigurableBeanFactory beanFactory, String listenerBeanName, ResolvableType eventType) {
      Class<?> listenerType = beanFactory.getType(listenerBeanName);
      if (listenerType == null
         || GenericApplicationListener.class.isAssignableFrom(listenerType)
         || SmartApplicationListener.class.isAssignableFrom(listenerType)) {
         return true;
      }

      if (!this.supportsEvent(listenerType, eventType)) {
         return false;
      }

      try {
         BeanDefinition bd = beanFactory.getMergedBeanDefinition(listenerBeanName);
         ResolvableType genericEventType = bd.getResolvableType().as(ApplicationListener.class).getGeneric(new int[0]);
         return genericEventType == ResolvableType.NONE || genericEventType.isAssignableFrom(eventType);
      } catch (NoSuchBeanDefinitionException ex) {
         return true;
      }
   }

   protected boolean supportsEvent(Class<?> listenerType, ResolvableType eventType) {
      ResolvableType declaredEventType = GenericApplicationListenerAdapter.resolveDeclaredEventType(listenerType);
      return declaredEventType == null || declaredEventType.isAssignableFrom(eventType);
   }

   protected boolean supportsEvent(ApplicationListener<?> listener, ResolvableType eventType, @Nullable Class<?> sourceType) {
      GenericApplicationListener smartListener = listener instanceof GenericApplicationListener gal ? gal : new GenericApplicationListenerAdapter(listener);
      return smartListener.supportsEventType(eventType) && smartListener.supportsSourceType(sourceType);
   }

   private class CachedListenerRetriever {
      public volatile @Nullable Set<ApplicationListener<?>> applicationListeners;
      public volatile @Nullable Set<String> applicationListenerBeans;

      public @Nullable Collection<ApplicationListener<?>> getApplicationListeners() {
         Set<ApplicationListener<?>> applicationListeners = this.applicationListeners;
         Set<String> applicationListenerBeans = this.applicationListenerBeans;
         if (applicationListeners != null && applicationListenerBeans != null) {
            List<ApplicationListener<?>> allListeners = new ArrayList<>(applicationListeners.size() + applicationListenerBeans.size());
            allListeners.addAll(applicationListeners);
            if (!applicationListenerBeans.isEmpty()) {
               BeanFactory beanFactory = AbstractApplicationEventMulticaster.this.getBeanFactory();

               for (String listenerBeanName : applicationListenerBeans) {
                  try {
                     allListeners.add((ApplicationListener<?>)beanFactory.getBean(listenerBeanName, ApplicationListener.class));
                  } catch (NoSuchBeanDefinitionException var8) {
                  }
               }
            }

            if (!applicationListenerBeans.isEmpty()) {
               AnnotationAwareOrderComparator.sort(allListeners);
            }

            return allListeners;
         } else {
            return null;
         }
      }
   }

   private class DefaultListenerRetriever {
      public final Set<ApplicationListener<?>> applicationListeners = new LinkedHashSet<>();
      public final Set<String> applicationListenerBeans = new LinkedHashSet<>();

      public Collection<ApplicationListener<?>> getApplicationListeners() {
         List<ApplicationListener<?>> allListeners = new ArrayList<>(this.applicationListeners.size() + this.applicationListenerBeans.size());
         allListeners.addAll(this.applicationListeners);
         if (!this.applicationListenerBeans.isEmpty()) {
            BeanFactory beanFactory = AbstractApplicationEventMulticaster.this.getBeanFactory();

            for (String listenerBeanName : this.applicationListenerBeans) {
               try {
                  ApplicationListener<?> listener = (ApplicationListener<?>)beanFactory.getBean(listenerBeanName, ApplicationListener.class);
                  if (!allListeners.contains(listener)) {
                     allListeners.add(listener);
                  }
               } catch (NoSuchBeanDefinitionException var6) {
               }
            }
         }

         AnnotationAwareOrderComparator.sort(allListeners);
         return allListeners;
      }
   }

   private static final class ListenerCacheKey implements Comparable<AbstractApplicationEventMulticaster.ListenerCacheKey> {
      private final ResolvableType eventType;
      private final @Nullable Class<?> sourceType;

      public ListenerCacheKey(ResolvableType eventType, @Nullable Class<?> sourceType) {
         Assert.notNull(eventType, "Event type must not be null");
         this.eventType = eventType;
         this.sourceType = sourceType;
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other
            || other instanceof AbstractApplicationEventMulticaster.ListenerCacheKey that
               && this.eventType.equals(that.eventType)
               && ObjectUtils.nullSafeEquals(this.sourceType, that.sourceType);
      }

      @Override
      public int hashCode() {
         return Objects.hash(this.eventType, this.sourceType);
      }

      @Override
      public String toString() {
         return "ListenerCacheKey [eventType = " + this.eventType + ", sourceType = " + this.sourceType + "]";
      }

      public int compareTo(AbstractApplicationEventMulticaster.ListenerCacheKey other) {
         int result = this.eventType.toString().compareTo(other.eventType.toString());
         if (result == 0) {
            if (this.sourceType == null) {
               return other.sourceType == null ? 0 : -1;
            }

            if (other.sourceType == null) {
               return 1;
            }

            result = this.sourceType.getName().compareTo(other.sourceType.getName());
         }

         return result;
      }
   }
}
