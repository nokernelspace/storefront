package org.springframework.beans.factory.support;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCreationNotAllowedException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.config.SingletonBeanRegistry;
import org.springframework.core.SimpleAliasRegistry;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class DefaultSingletonBeanRegistry extends SimpleAliasRegistry implements SingletonBeanRegistry {
   private static final int SUPPRESSED_EXCEPTIONS_LIMIT = 100;
   final Lock singletonLock = new ReentrantLock();
   private final Map<String, Object> singletonObjects = new ConcurrentHashMap<>(256);
   private final Map<String, ObjectFactory<?>> singletonFactories = new ConcurrentHashMap<>(16);
   private final Map<String, Consumer<Object>> singletonCallbacks = new ConcurrentHashMap<>(16);
   private final Map<String, Object> earlySingletonObjects = new ConcurrentHashMap<>(16);
   private final Set<String> registeredSingletons = Collections.synchronizedSet(new LinkedHashSet<>(256));
   private final Set<String> singletonsCurrentlyInCreation = ConcurrentHashMap.newKeySet(16);
   private final Set<String> inCreationCheckExclusions = ConcurrentHashMap.newKeySet(16);
   private final Lock lenientCreationLock = new ReentrantLock();
   private final Condition lenientCreationFinished = this.lenientCreationLock.newCondition();
   private final Set<String> singletonsInLenientCreation = new HashSet<>();
   private final Map<Thread, Thread> lenientWaitingThreads = new HashMap<>();
   private final Map<String, Thread> currentCreationThreads = new ConcurrentHashMap<>();
   private volatile boolean singletonsCurrentlyInDestruction = false;
   private @Nullable Set<Exception> suppressedExceptions;
   private final Map<String, DisposableBean> disposableBeans = new LinkedHashMap<>();
   private final Map<String, Set<String>> containedBeanMap = new ConcurrentHashMap<>(16);
   private final Map<String, Set<String>> dependentBeanMap = new ConcurrentHashMap<>(64);
   private final Map<String, Set<String>> dependenciesForBeanMap = new ConcurrentHashMap<>(64);

   @Override
   public void registerSingleton(String beanName, Object singletonObject) throws IllegalStateException {
      Assert.notNull(beanName, "Bean name must not be null");
      Assert.notNull(singletonObject, "Singleton object must not be null");
      this.singletonLock.lock();

      try {
         this.addSingleton(beanName, singletonObject);
      } finally {
         this.singletonLock.unlock();
      }
   }

   protected void addSingleton(String beanName, Object singletonObject) {
      Object oldObject = this.singletonObjects.putIfAbsent(beanName, singletonObject);
      if (oldObject != null) {
         throw new IllegalStateException(
            "Could not register object [" + singletonObject + "] under bean name '" + beanName + "': there is already object [" + oldObject + "] bound"
         );
      }

      this.singletonFactories.remove(beanName);
      this.earlySingletonObjects.remove(beanName);
      this.registeredSingletons.add(beanName);
      Consumer<Object> callback = this.singletonCallbacks.get(beanName);
      if (callback != null) {
         callback.accept(singletonObject);
      }
   }

   protected void addSingletonFactory(String beanName, ObjectFactory<?> singletonFactory) {
      Assert.notNull(singletonFactory, "Singleton factory must not be null");
      this.singletonFactories.put(beanName, singletonFactory);
      this.earlySingletonObjects.remove(beanName);
      this.registeredSingletons.add(beanName);
   }

   @Override
   public void addSingletonCallback(String beanName, Consumer<Object> singletonConsumer) {
      this.singletonCallbacks.put(beanName, singletonConsumer);
   }

   @Override
   public @Nullable Object getSingleton(String beanName) {
      return this.getSingleton(beanName, true);
   }

   protected @Nullable Object getSingleton(String beanName, boolean allowEarlyReference) {
      Object singletonObject = this.singletonObjects.get(beanName);
      if (singletonObject == null && this.isSingletonCurrentlyInCreation(beanName)) {
         singletonObject = this.earlySingletonObjects.get(beanName);
         if (singletonObject == null && allowEarlyReference) {
            if (!this.singletonLock.tryLock()) {
               return null;
            }

            try {
               singletonObject = this.singletonObjects.get(beanName);
               if (singletonObject == null) {
                  singletonObject = this.earlySingletonObjects.get(beanName);
                  if (singletonObject == null) {
                     ObjectFactory<?> singletonFactory = this.singletonFactories.get(beanName);
                     if (singletonFactory != null) {
                        singletonObject = singletonFactory.getObject();
                        if (this.singletonFactories.remove(beanName) != null) {
                           this.earlySingletonObjects.put(beanName, singletonObject);
                        } else {
                           singletonObject = this.singletonObjects.get(beanName);
                        }
                     }
                  }
               }
            } finally {
               this.singletonLock.unlock();
            }
         }
      }

      return singletonObject;
   }

   public Object getSingleton(String beanName, ObjectFactory<?> singletonFactory) {
      Assert.notNull(beanName, "Bean name must not be null");
      Thread currentThread = Thread.currentThread();
      Boolean lockFlag = this.isCurrentThreadAllowedToHoldSingletonLock();
      boolean acquireLock = !Boolean.FALSE.equals(lockFlag);
      boolean locked = acquireLock && this.singletonLock.tryLock();

      try {
         Object singletonObject = this.singletonObjects.get(beanName);
         if (singletonObject == null) {
            if (acquireLock && !locked) {
               if (Boolean.TRUE.equals(lockFlag)) {
                  this.lenientCreationLock.lock();

                  try {
                     if (this.logger.isInfoEnabled()) {
                        Set<String> lockedBeans = new HashSet<>(this.singletonsCurrentlyInCreation);
                        lockedBeans.removeAll(this.singletonsInLenientCreation);
                        this.logger
                           .info(
                              "Obtaining singleton bean '"
                                 + beanName
                                 + "' in thread \""
                                 + currentThread.getName()
                                 + "\" while other thread holds singleton lock for other beans "
                                 + lockedBeans
                           );
                     }

                     this.singletonsInLenientCreation.add(beanName);
                  } finally {
                     this.lenientCreationLock.unlock();
                  }
               } else {
                  this.singletonLock.lock();
                  locked = true;
                  singletonObject = this.singletonObjects.get(beanName);
                  if (singletonObject != null) {
                     return singletonObject;
                  }
               }
            }

            if (this.singletonsCurrentlyInDestruction) {
               throw new BeanCreationNotAllowedException(
                  beanName,
                  "Singleton bean creation not allowed while singletons of this factory are in destruction (Do not request a bean from a BeanFactory in a destroy method implementation!)"
               );
            }

            if (this.logger.isDebugEnabled()) {
               this.logger.debug("Creating shared instance of singleton bean '" + beanName + "'");
            }

            try {
               this.beforeSingletonCreation(beanName);
            } catch (BeanCurrentlyInCreationException var186) {
               BeanCurrentlyInCreationException ex = var186;
               this.lenientCreationLock.lock();

               try {
                  while ((singletonObject = this.singletonObjects.get(beanName)) == null) {
                     Thread otherThread = this.currentCreationThreads.get(beanName);
                     if (otherThread == null || otherThread != currentThread && !this.checkDependentWaitingThreads(otherThread, currentThread)) {
                        if (this.singletonsInLenientCreation.contains(beanName)) {
                           if (otherThread != null) {
                              this.lenientWaitingThreads.put(currentThread, otherThread);
                           }

                           try {
                              try {
                                 this.lenientCreationFinished.await();
                              } catch (InterruptedException ie) {
                                 currentThread.interrupt();
                              }
                              continue;
                           } finally {
                              if (otherThread != null) {
                                 this.lenientWaitingThreads.remove(currentThread);
                              }
                           }
                        }
                        break;
                     }

                     throw ex;
                  }
               } finally {
                  this.lenientCreationLock.unlock();
               }

               if (singletonObject != null) {
                  return singletonObject;
               }

               if (locked) {
                  throw var186;
               }

               this.singletonLock.lock();
               locked = true;
               singletonObject = this.singletonObjects.get(beanName);
               if (singletonObject != null) {
                  return singletonObject;
               }

               this.beforeSingletonCreation(beanName);
            }

            boolean newSingleton = false;
            boolean recordSuppressedExceptions = locked && this.suppressedExceptions == null;
            if (recordSuppressedExceptions) {
               this.suppressedExceptions = new LinkedHashSet<>();
            }

            try {
               singletonObject = this.singletonObjects.get(beanName);
               if (singletonObject == null) {
                  this.currentCreationThreads.put(beanName, currentThread);

                  try {
                     singletonObject = singletonFactory.getObject();
                  } finally {
                     this.currentCreationThreads.remove(beanName);
                  }

                  newSingleton = true;
               }
            } catch (IllegalStateException ex) {
               singletonObject = this.singletonObjects.get(beanName);
               if (singletonObject == null) {
                  throw ex;
               }
            } catch (BeanCreationException var183) {
               BeanCreationException ex = var183;
               if (recordSuppressedExceptions) {
                  for (Exception suppressedException : this.suppressedExceptions) {
                     ex.addRelatedCause(suppressedException);
                  }
               }

               throw ex;
            } finally {
               if (recordSuppressedExceptions) {
                  this.suppressedExceptions = null;
               }

               this.afterSingletonCreation(beanName);
            }

            if (newSingleton) {
               try {
                  this.addSingleton(beanName, singletonObject);
               } catch (IllegalStateException ex) {
                  Object object = this.singletonObjects.get(beanName);
                  if (singletonObject != object) {
                     throw ex;
                  }
               }
            }
         }

         return singletonObject;
      } finally {
         if (locked) {
            this.singletonLock.unlock();
         }

         this.lenientCreationLock.lock();

         try {
            this.singletonsInLenientCreation.remove(beanName);
            this.lenientWaitingThreads.entrySet().removeIf(entry -> entry.getValue() == currentThread);
            this.lenientCreationFinished.signalAll();
         } finally {
            this.lenientCreationLock.unlock();
         }
      }
   }

   private boolean checkDependentWaitingThreads(Thread waitingThread, Thread candidateThread) {
      Thread threadToCheck = waitingThread;

      while ((threadToCheck = this.lenientWaitingThreads.get(threadToCheck)) != null) {
         if (threadToCheck == candidateThread) {
            return true;
         }
      }

      return false;
   }

   protected @Nullable Boolean isCurrentThreadAllowedToHoldSingletonLock() {
      return null;
   }

   protected void onSuppressedException(Exception ex) {
      if (this.suppressedExceptions != null && this.suppressedExceptions.size() < 100) {
         this.suppressedExceptions.add(ex);
      }
   }

   protected void removeSingleton(String beanName) {
      this.singletonObjects.remove(beanName);
      this.singletonFactories.remove(beanName);
      this.earlySingletonObjects.remove(beanName);
      this.registeredSingletons.remove(beanName);
   }

   @Override
   public boolean containsSingleton(String beanName) {
      return this.singletonObjects.containsKey(beanName);
   }

   @Override
   public String[] getSingletonNames() {
      return StringUtils.toStringArray(this.registeredSingletons);
   }

   @Override
   public int getSingletonCount() {
      return this.registeredSingletons.size();
   }

   public void setCurrentlyInCreation(String beanName, boolean inCreation) {
      Assert.notNull(beanName, "Bean name must not be null");
      if (!inCreation) {
         this.inCreationCheckExclusions.add(beanName);
      } else {
         this.inCreationCheckExclusions.remove(beanName);
      }
   }

   public boolean isCurrentlyInCreation(String beanName) {
      Assert.notNull(beanName, "Bean name must not be null");
      return !this.inCreationCheckExclusions.contains(beanName) && this.isActuallyInCreation(beanName);
   }

   protected boolean isActuallyInCreation(String beanName) {
      return this.isSingletonCurrentlyInCreation(beanName);
   }

   public boolean isSingletonCurrentlyInCreation(@Nullable String beanName) {
      return this.singletonsCurrentlyInCreation.contains(beanName);
   }

   protected void beforeSingletonCreation(String beanName) {
      if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.add(beanName)) {
         throw new BeanCurrentlyInCreationException(beanName);
      }
   }

   protected void afterSingletonCreation(String beanName) {
      if (!this.inCreationCheckExclusions.contains(beanName) && !this.singletonsCurrentlyInCreation.remove(beanName)) {
         throw new IllegalStateException("Singleton '" + beanName + "' isn't currently in creation");
      }
   }

   public void registerDisposableBean(String beanName, DisposableBean bean) {
      synchronized (this.disposableBeans) {
         this.disposableBeans.put(beanName, bean);
      }
   }

   public void registerContainedBean(String containedBeanName, String containingBeanName) {
      synchronized (this.containedBeanMap) {
         Set<String> containedBeans = this.containedBeanMap.computeIfAbsent(containingBeanName, key -> new LinkedHashSet<>(8));
         if (!containedBeans.add(containedBeanName)) {
            return;
         }
      }

      this.registerDependentBean(containedBeanName, containingBeanName);
   }

   public void registerDependentBean(String beanName, String dependentBeanName) {
      String canonicalName = this.canonicalName(beanName);
      synchronized (this.dependentBeanMap) {
         Set<String> dependentBeans = this.dependentBeanMap.computeIfAbsent(canonicalName, key -> new LinkedHashSet<>(8));
         if (!dependentBeans.add(dependentBeanName)) {
            return;
         }
      }

      synchronized (this.dependenciesForBeanMap) {
         Set<String> dependenciesForBean = this.dependenciesForBeanMap.computeIfAbsent(dependentBeanName, key -> new LinkedHashSet<>(8));
         dependenciesForBean.add(canonicalName);
      }
   }

   protected boolean isDependent(String beanName, String dependentBeanName) {
      synchronized (this.dependentBeanMap) {
         return this.isDependent(beanName, dependentBeanName, null);
      }
   }

   private boolean isDependent(String beanName, String dependentBeanName, @Nullable Set<String> alreadySeen) {
      if (alreadySeen != null && alreadySeen.contains(beanName)) {
         return false;
      }

      String canonicalName = this.canonicalName(beanName);
      Set<String> dependentBeans = this.dependentBeanMap.get(canonicalName);
      if (dependentBeans != null && !dependentBeans.isEmpty()) {
         if (dependentBeans.contains(dependentBeanName)) {
            return true;
         }

         if (alreadySeen == null) {
            alreadySeen = new HashSet<>();
         }

         alreadySeen.add(beanName);

         for (String transitiveDependency : dependentBeans) {
            if (this.isDependent(transitiveDependency, dependentBeanName, alreadySeen)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   protected boolean hasDependentBean(String beanName) {
      return this.dependentBeanMap.containsKey(beanName);
   }

   public String[] getDependentBeans(String beanName) {
      Set<String> dependentBeans = this.dependentBeanMap.get(beanName);
      if (dependentBeans == null) {
         return new String[0];
      }

      synchronized (this.dependentBeanMap) {
         return StringUtils.toStringArray(dependentBeans);
      }
   }

   public String[] getDependenciesForBean(String beanName) {
      Set<String> dependenciesForBean = this.dependenciesForBeanMap.get(beanName);
      if (dependenciesForBean == null) {
         return new String[0];
      }

      synchronized (this.dependenciesForBeanMap) {
         return StringUtils.toStringArray(dependenciesForBean);
      }
   }

   public void destroySingletons() {
      if (this.logger.isTraceEnabled()) {
         this.logger.trace("Destroying singletons in " + this);
      }

      this.singletonsCurrentlyInDestruction = true;
      String[] disposableBeanNames;
      synchronized (this.disposableBeans) {
         disposableBeanNames = StringUtils.toStringArray(this.disposableBeans.keySet());
      }

      for (int i = disposableBeanNames.length - 1; i >= 0; i--) {
         this.destroySingleton(disposableBeanNames[i]);
      }

      this.containedBeanMap.clear();
      this.dependentBeanMap.clear();
      this.dependenciesForBeanMap.clear();
      this.singletonLock.lock();

      try {
         this.clearSingletonCache();
      } finally {
         this.singletonLock.unlock();
      }
   }

   protected void clearSingletonCache() {
      this.singletonObjects.clear();
      this.singletonFactories.clear();
      this.earlySingletonObjects.clear();
      this.registeredSingletons.clear();
      this.singletonsCurrentlyInDestruction = false;
   }

   public void destroySingleton(String beanName) {
      DisposableBean disposableBean;
      synchronized (this.disposableBeans) {
         disposableBean = this.disposableBeans.remove(beanName);
      }

      this.destroyBean(beanName, disposableBean);
      if (!this.singletonsCurrentlyInDestruction) {
         if (this.currentCreationThreads.get(beanName) == Thread.currentThread()) {
            this.removeSingleton(beanName);
         } else {
            this.singletonLock.lock();

            try {
               this.removeSingleton(beanName);
            } finally {
               this.singletonLock.unlock();
            }
         }
      }
   }

   protected void destroyBean(String beanName, @Nullable DisposableBean bean) {
      Set<String> dependentBeanNames;
      synchronized (this.dependentBeanMap) {
         dependentBeanNames = this.dependentBeanMap.remove(beanName);
      }

      if (dependentBeanNames != null) {
         if (this.logger.isTraceEnabled()) {
            this.logger.trace("Retrieved dependent beans for bean '" + beanName + "': " + dependentBeanNames);
         }

         for (String dependentBeanName : dependentBeanNames) {
            this.destroySingleton(dependentBeanName);
         }
      }

      if (bean != null) {
         try {
            bean.destroy();
         } catch (Throwable ex) {
            if (this.logger.isWarnEnabled()) {
               this.logger.warn("Destruction of bean with name '" + beanName + "' threw an exception", ex);
            }
         }
      }

      Set<String> containedBeans;
      synchronized (this.containedBeanMap) {
         containedBeans = this.containedBeanMap.remove(beanName);
      }

      if (containedBeans != null) {
         for (String containedBeanName : containedBeans) {
            this.destroySingleton(containedBeanName);
         }
      }

      synchronized (this.dependentBeanMap) {
         Iterator<Entry<String, Set<String>>> it = this.dependentBeanMap.entrySet().iterator();

         while (it.hasNext()) {
            Entry<String, Set<String>> entry = it.next();
            Set<String> dependenciesToClean = entry.getValue();
            dependenciesToClean.remove(beanName);
            if (dependenciesToClean.isEmpty()) {
               it.remove();
            }
         }
      }

      this.dependenciesForBeanMap.remove(beanName);
   }

   @Deprecated(since = "6.2")
   @Override
   public final Object getSingletonMutex() {
      return new Object();
   }
}
