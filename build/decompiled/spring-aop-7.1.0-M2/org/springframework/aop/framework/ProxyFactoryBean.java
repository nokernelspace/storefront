package org.springframework.aop.framework;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.aopalliance.aop.Advice;
import org.aopalliance.intercept.Interceptor;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.Advisor;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.adapter.AdvisorAdapterRegistry;
import org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry;
import org.springframework.aop.framework.adapter.UnknownAdviceTypeException;
import org.springframework.aop.target.SingletonTargetSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.FactoryBeanNotInitializedException;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;

public class ProxyFactoryBean extends ProxyCreatorSupport implements FactoryBean<Object>, BeanClassLoaderAware, BeanFactoryAware {
   public static final String GLOBAL_SUFFIX = "*";
   private static final Log logger = LogFactory.getLog(ProxyFactoryBean.class);
   private String @Nullable [] interceptorNames;
   private @Nullable String targetName;
   private boolean autodetectInterfaces = true;
   private boolean singleton = true;
   private AdvisorAdapterRegistry advisorAdapterRegistry = GlobalAdvisorAdapterRegistry.getInstance();
   private boolean freezeProxy = false;
   private transient @Nullable ClassLoader proxyClassLoader = ClassUtils.getDefaultClassLoader();
   private transient boolean classLoaderConfigured = false;
   private transient @Nullable BeanFactory beanFactory;
   private boolean advisorChainInitialized = false;
   private @Nullable Object singletonInstance;

   public void setProxyInterfaces(Class<?>[] proxyInterfaces) throws ClassNotFoundException {
      this.setInterfaces(proxyInterfaces);
   }

   public void setInterceptorNames(String... interceptorNames) {
      this.interceptorNames = interceptorNames;
   }

   public void setTargetName(String targetName) {
      this.targetName = targetName;
   }

   public void setAutodetectInterfaces(boolean autodetectInterfaces) {
      this.autodetectInterfaces = autodetectInterfaces;
   }

   public void setSingleton(boolean singleton) {
      this.singleton = singleton;
   }

   public void setAdvisorAdapterRegistry(AdvisorAdapterRegistry advisorAdapterRegistry) {
      this.advisorAdapterRegistry = advisorAdapterRegistry;
   }

   @Override
   public void setFrozen(boolean frozen) {
      this.freezeProxy = frozen;
   }

   public void setProxyClassLoader(@Nullable ClassLoader classLoader) {
      this.proxyClassLoader = classLoader;
      this.classLoaderConfigured = classLoader != null;
   }

   public void setBeanClassLoader(ClassLoader classLoader) {
      if (!this.classLoaderConfigured) {
         this.proxyClassLoader = classLoader;
      }
   }

   public void setBeanFactory(BeanFactory beanFactory) {
      this.beanFactory = beanFactory;
      this.checkInterceptorNames();
   }

   public @Nullable Object getObject() throws BeansException {
      this.initializeAdvisorChain();
      if (this.isSingleton()) {
         return this.getSingletonInstance();
      }

      if (this.targetName == null) {
         logger.info("Using non-singleton proxies with singleton targets is often undesirable. Enable prototype proxies by setting the 'targetName' property.");
      }

      return this.newPrototypeInstance();
   }

   public @Nullable Class<?> getObjectType() {
      synchronized (this) {
         if (this.singletonInstance != null) {
            return this.singletonInstance.getClass();
         }
      }

      try {
         return this.createAopProxy().getProxyClass(this.proxyClassLoader);
      } catch (AopConfigException ex) {
         if (this.getTargetClass() == null) {
            if (logger.isDebugEnabled()) {
               logger.debug("Failed to determine early proxy class: " + ex.getMessage());
            }

            return null;
         } else {
            throw ex;
         }
      }
   }

   public boolean isSingleton() {
      return this.singleton;
   }

   private synchronized Object getSingletonInstance() {
      if (this.singletonInstance == null) {
         this.targetSource = this.freshTargetSource();
         if (this.autodetectInterfaces && this.getProxiedInterfaces().length == 0 && !this.isProxyTargetClass()) {
            Class<?> targetClass = this.getTargetClass();
            if (targetClass == null) {
               throw new FactoryBeanNotInitializedException("Cannot determine target class for proxy");
            }

            this.setInterfaces(ClassUtils.getAllInterfacesForClass(targetClass, this.proxyClassLoader));
         }

         super.setFrozen(this.freezeProxy);
         this.singletonInstance = this.getProxy(this.createAopProxy());
      }

      return this.singletonInstance;
   }

   private synchronized Object newPrototypeInstance() {
      ProxyCreatorSupport copy = new ProxyCreatorSupport(this.getAopProxyFactory());
      TargetSource targetSource = this.freshTargetSource();
      copy.copyConfigurationFrom(this, targetSource, this.freshAdvisorChain());
      if (this.autodetectInterfaces && this.getProxiedInterfaces().length == 0 && !this.isProxyTargetClass()) {
         Class<?> targetClass = targetSource.getTargetClass();
         if (targetClass != null) {
            copy.setInterfaces(ClassUtils.getAllInterfacesForClass(targetClass, this.proxyClassLoader));
         }
      }

      copy.setFrozen(this.freezeProxy);
      return this.getProxy(copy.createAopProxy());
   }

   protected Object getProxy(AopProxy aopProxy) {
      return aopProxy.getProxy(this.proxyClassLoader);
   }

   private void checkInterceptorNames() {
      if (!ObjectUtils.isEmpty(this.interceptorNames)) {
         String finalName = this.interceptorNames[this.interceptorNames.length - 1];
         if (this.targetName == null && this.targetSource == EMPTY_TARGET_SOURCE && !finalName.endsWith("*") && !this.isNamedBeanAnAdvisorOrAdvice(finalName)) {
            this.targetName = finalName;
            if (logger.isDebugEnabled()) {
               logger.debug("Bean with name '" + finalName + "' concluding interceptor chain is not an advisor class: treating it as a target or TargetSource");
            }

            this.interceptorNames = Arrays.copyOf(this.interceptorNames, this.interceptorNames.length - 1);
         }
      }
   }

   private boolean isNamedBeanAnAdvisorOrAdvice(String beanName) {
      Assert.state(this.beanFactory != null, "No BeanFactory set");
      Class<?> namedBeanClass = this.beanFactory.getType(beanName);
      if (namedBeanClass == null) {
         if (logger.isDebugEnabled()) {
            logger.debug("Could not determine type of bean with name '" + beanName + "' - assuming it is neither an Advisor nor an Advice");
         }

         return false;
      } else {
         return Advisor.class.isAssignableFrom(namedBeanClass) || Advice.class.isAssignableFrom(namedBeanClass);
      }
   }

   private synchronized void initializeAdvisorChain() throws AopConfigException, BeansException {
      if (!this.advisorChainInitialized && !ObjectUtils.isEmpty(this.interceptorNames)) {
         if (this.beanFactory == null) {
            throw new IllegalStateException(
               "No BeanFactory available anymore (probably due to serialization) - cannot resolve interceptor names " + Arrays.toString(this.interceptorNames)
            );
         }

         if (this.interceptorNames[this.interceptorNames.length - 1].endsWith("*") && this.targetName == null && this.targetSource == EMPTY_TARGET_SOURCE) {
            throw new AopConfigException("Target required after globals");
         }

         for (String name : this.interceptorNames) {
            if (name.endsWith("*")) {
               if (!(this.beanFactory instanceof ListableBeanFactory lbf)) {
                  throw new AopConfigException("Can only use global advisors or interceptors with a ListableBeanFactory");
               }

               this.addGlobalAdvisors(lbf, name.substring(0, name.length() - "*".length()));
            } else {
               Object advice;
               if (!this.singleton && !this.beanFactory.isSingleton(name)) {
                  advice = new ProxyFactoryBean.PrototypePlaceholderAdvisor(name);
               } else {
                  advice = this.beanFactory.getBean(name);
               }

               this.addAdvisorOnChainCreation(advice);
            }
         }

         this.advisorChainInitialized = true;
      }
   }

   private List<Advisor> freshAdvisorChain() {
      Advisor[] advisors = this.getAdvisors();
      List<Advisor> freshAdvisors = new ArrayList<>(advisors.length);

      for (Advisor advisor : advisors) {
         if (advisor instanceof ProxyFactoryBean.PrototypePlaceholderAdvisor ppa) {
            if (logger.isDebugEnabled()) {
               logger.debug("Refreshing bean named '" + ppa.getBeanName() + "'");
            }

            if (this.beanFactory == null) {
               throw new IllegalStateException(
                  "No BeanFactory available anymore (probably due to serialization) - cannot resolve prototype advisor '" + ppa.getBeanName() + "'"
               );
            }

            Object bean = this.beanFactory.getBean(ppa.getBeanName());
            Advisor refreshedAdvisor = this.namedBeanToAdvisor(bean);
            freshAdvisors.add(refreshedAdvisor);
         } else {
            freshAdvisors.add(advisor);
         }
      }

      return freshAdvisors;
   }

   private void addGlobalAdvisors(ListableBeanFactory beanFactory, String prefix) {
      String[] globalAdvisorNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(beanFactory, Advisor.class);
      String[] globalInterceptorNames = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(beanFactory, Interceptor.class);
      if (globalAdvisorNames.length > 0 || globalInterceptorNames.length > 0) {
         List<Object> beans = new ArrayList<>(globalAdvisorNames.length + globalInterceptorNames.length);

         for (String name : globalAdvisorNames) {
            if (name.startsWith(prefix)) {
               beans.add(beanFactory.getBean(name));
            }
         }

         for (String name : globalInterceptorNames) {
            if (name.startsWith(prefix)) {
               beans.add(beanFactory.getBean(name));
            }
         }

         AnnotationAwareOrderComparator.sort(beans);

         for (Object bean : beans) {
            this.addAdvisorOnChainCreation(bean);
         }
      }
   }

   private void addAdvisorOnChainCreation(Object next) {
      this.addAdvisor(this.namedBeanToAdvisor(next));
   }

   private TargetSource freshTargetSource() {
      if (this.targetName == null) {
         return this.targetSource;
      }

      if (this.beanFactory == null) {
         throw new IllegalStateException(
            "No BeanFactory available anymore (probably due to serialization) - cannot resolve target with name '" + this.targetName + "'"
         );
      }

      if (logger.isDebugEnabled()) {
         logger.debug("Refreshing target with name '" + this.targetName + "'");
      }

      Object target = this.beanFactory.getBean(this.targetName);
      return target instanceof TargetSource targetSource ? targetSource : new SingletonTargetSource(target);
   }

   private Advisor namedBeanToAdvisor(Object next) {
      try {
         return this.advisorAdapterRegistry.wrap(next);
      } catch (UnknownAdviceTypeException ex) {
         throw new AopConfigException(
            "Unknown advisor type "
               + next.getClass()
               + "; can only include Advisor or Advice type beans in interceptorNames chain except for last entry which may also be target instance or TargetSource",
            ex
         );
      }
   }

   @Override
   protected void adviceChanged() {
      super.adviceChanged();
      if (this.singleton) {
         logger.debug("Advice has changed; re-caching singleton instance");
         synchronized (this) {
            this.singletonInstance = null;
         }
      }
   }

   private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
      ois.defaultReadObject();
      this.proxyClassLoader = ClassUtils.getDefaultClassLoader();
   }

   private static class PrototypePlaceholderAdvisor implements Advisor, Serializable {
      private final String beanName;
      private final String message;

      public PrototypePlaceholderAdvisor(String beanName) {
         this.beanName = beanName;
         this.message = "Placeholder for prototype Advisor/Advice with bean name '" + beanName + "'";
      }

      public String getBeanName() {
         return this.beanName;
      }

      @Override
      public Advice getAdvice() {
         throw new UnsupportedOperationException("Cannot invoke methods: " + this.message);
      }

      @Override
      public String toString() {
         return this.message;
      }
   }
}
