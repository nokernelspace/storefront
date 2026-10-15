package org.springframework.beans.factory.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.FactoryBeanNotInitializedException;
import org.springframework.beans.factory.SmartFactoryBean;
import org.springframework.core.AttributeAccessor;
import org.springframework.core.ResolvableType;

public abstract class FactoryBeanRegistrySupport extends DefaultSingletonBeanRegistry {
   private final Map<String, Object> factoryBeanObjectCache = new ConcurrentHashMap<>(16);

   protected @Nullable Class<?> getTypeForFactoryBean(FactoryBean<?> factoryBean) {
      try {
         return factoryBean.getObjectType();
      } catch (Throwable ex) {
         this.logger
            .info(
               "FactoryBean threw exception from getObjectType, despite the contract saying that it should return null if the type of its object cannot be determined yet",
               ex
            );
         return null;
      }
   }

   ResolvableType getTypeForFactoryBeanFromAttributes(AttributeAccessor attributes) {
      Object attribute = attributes.getAttribute("factoryBeanObjectType");
      if (attribute == null) {
         return ResolvableType.NONE;
      } else if (attribute instanceof ResolvableType resolvableType) {
         return resolvableType;
      } else if (attribute instanceof Class<?> clazz) {
         return ResolvableType.forClass(clazz);
      } else {
         throw new IllegalArgumentException("Invalid value type for attribute 'factoryBeanObjectType': " + attribute.getClass().getName());
      }
   }

   ResolvableType getFactoryBeanGeneric(@Nullable ResolvableType type) {
      return type != null ? type.as(FactoryBean.class).getGeneric(new int[0]) : ResolvableType.NONE;
   }

   protected @Nullable Object getCachedObjectForFactoryBean(String beanName) {
      return this.factoryBeanObjectCache.get(beanName);
   }

   protected Object getObjectFromFactoryBean(FactoryBean<?> factory, @Nullable Class<?> requiredType, String beanName, boolean shouldPostProcess) {
      if (factory.isSingleton() && this.containsSingleton(beanName)) {
         Boolean lockFlag = this.isCurrentThreadAllowedToHoldSingletonLock();
         boolean locked;
         if (lockFlag == null) {
            this.singletonLock.lock();
            locked = true;
         } else {
            locked = lockFlag && this.singletonLock.tryLock();
         }

         Object alreadyThere;
         try {
            if (factory instanceof SmartFactoryBean) {
               Object object = this.doGetObjectFromFactoryBean(factory, requiredType, beanName);
               if (shouldPostProcess) {
                  object = this.postProcessObjectFromSingletonFactoryBean(object, beanName, locked);
               }

               return object;
            }

            synchronized (factory) {
               Object object = this.factoryBeanObjectCache.get(beanName);
               if (object == null) {
                  object = this.doGetObjectFromFactoryBean(factory, requiredType, beanName);
                  alreadyThere = this.factoryBeanObjectCache.get(beanName);
                  if (alreadyThere != null) {
                     object = alreadyThere;
                  } else {
                     if (shouldPostProcess) {
                        object = this.postProcessObjectFromSingletonFactoryBean(object, beanName, locked);
                     }

                     if (this.containsSingleton(beanName)) {
                        this.factoryBeanObjectCache.put(beanName, object);
                     }
                  }
               }

               alreadyThere = object;
            }
         } finally {
            if (locked) {
               this.singletonLock.unlock();
            }
         }

         return alreadyThere;
      } else {
         Object object = this.doGetObjectFromFactoryBean(factory, requiredType, beanName);
         if (shouldPostProcess) {
            try {
               object = this.postProcessObjectFromFactoryBean(object, beanName);
            } catch (Throwable ex) {
               throw new BeanCreationException(beanName, "Post-processing of FactoryBean's object failed", ex);
            }
         }

         return object;
      }
   }

   private Object doGetObjectFromFactoryBean(FactoryBean<?> factory, @Nullable Class<?> requiredType, String beanName) throws BeanCreationException {
      Object object;
      try {
         object = requiredType != null && factory instanceof SmartFactoryBean<?> smartFactoryBean
            ? smartFactoryBean.getObject(requiredType)
            : factory.getObject();
      } catch (FactoryBeanNotInitializedException ex) {
         throw new BeanCurrentlyInCreationException(beanName, ex.toString());
      } catch (Throwable ex) {
         throw new BeanCreationException(beanName, "FactoryBean threw exception on object creation", ex);
      }

      if (object == null) {
         if (this.isSingletonCurrentlyInCreation(beanName)) {
            throw new BeanCurrentlyInCreationException(beanName, "FactoryBean which is currently in creation returned null from getObject");
         }

         object = new NullBean();
      }

      return object;
   }

   private Object postProcessObjectFromSingletonFactoryBean(Object object, String beanName, boolean locked) {
      if (locked) {
         if (this.isSingletonCurrentlyInCreation(beanName)) {
            return object;
         }

         this.beforeSingletonCreation(beanName);
      }

      try {
         return this.postProcessObjectFromFactoryBean(object, beanName);
      } catch (Throwable ex) {
         throw new BeanCreationException(beanName, "Post-processing of FactoryBean's singleton object failed", ex);
      } finally {
         if (locked) {
            this.afterSingletonCreation(beanName);
         }
      }
   }

   protected Object postProcessObjectFromFactoryBean(Object object, String beanName) throws BeansException {
      return object;
   }

   protected FactoryBean<?> getFactoryBean(String beanName, Object beanInstance) throws BeansException {
      if (beanInstance instanceof FactoryBean<?> factoryBean) {
         return factoryBean;
      } else {
         throw new BeanCreationException(beanName, "Bean instance of type [" + beanInstance.getClass() + "] is not a FactoryBean");
      }
   }

   @Override
   protected void removeSingleton(String beanName) {
      super.removeSingleton(beanName);
      this.factoryBeanObjectCache.remove(beanName);
   }

   @Override
   protected void clearSingletonCache() {
      super.clearSingletonCache();
      this.factoryBeanObjectCache.clear();
   }
}
