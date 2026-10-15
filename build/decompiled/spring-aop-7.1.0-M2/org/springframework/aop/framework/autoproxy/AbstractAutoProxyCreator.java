package org.springframework.aop.framework.autoproxy;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.aopalliance.aop.Advice;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.Advisor;
import org.springframework.aop.Pointcut;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.framework.ProxyProcessorSupport;
import org.springframework.aop.framework.adapter.AdvisorAdapterRegistry;
import org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry;
import org.springframework.aop.target.EmptyTargetSource;
import org.springframework.aop.target.SingletonTargetSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.core.SmartClassLoader;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.StringUtils;

public abstract class AbstractAutoProxyCreator extends ProxyProcessorSupport implements SmartInstantiationAwareBeanPostProcessor, BeanFactoryAware {
   protected static final Object @Nullable [] DO_NOT_PROXY = null;
   protected static final Object[] PROXY_WITHOUT_ADDITIONAL_INTERCEPTORS = new Object[0];
   protected final Log logger = LogFactory.getLog(this.getClass());
   private AdvisorAdapterRegistry advisorAdapterRegistry = GlobalAdvisorAdapterRegistry.getInstance();
   private String[] interceptorNames = new String[0];
   private boolean applyCommonInterceptorsFirst = true;
   private TargetSourceCreator @Nullable [] customTargetSourceCreators;
   private @Nullable BeanFactory beanFactory;
   private final Set<String> targetSourcedBeans = ConcurrentHashMap.newKeySet(16);
   private final Map<Object, Object> earlyBeanReferences = new ConcurrentHashMap<>(16);
   private final Map<Object, Class<?>> proxyTypes = new ConcurrentHashMap<>(16);
   private final Map<Object, Boolean> advisedBeans = new ConcurrentHashMap<>(256);

   public void setAdvisorAdapterRegistry(AdvisorAdapterRegistry advisorAdapterRegistry) {
      this.advisorAdapterRegistry = advisorAdapterRegistry;
   }

   public void setCustomTargetSourceCreators(TargetSourceCreator... targetSourceCreators) {
      this.customTargetSourceCreators = targetSourceCreators;
   }

   public void setInterceptorNames(String... interceptorNames) {
      this.interceptorNames = interceptorNames;
   }

   public void setApplyCommonInterceptorsFirst(boolean applyCommonInterceptorsFirst) {
      this.applyCommonInterceptorsFirst = applyCommonInterceptorsFirst;
   }

   public void setBeanFactory(BeanFactory beanFactory) {
      this.beanFactory = beanFactory;
      AutoProxyUtils.applyDefaultProxyConfig(this, beanFactory);
   }

   protected @Nullable BeanFactory getBeanFactory() {
      return this.beanFactory;
   }

   public @Nullable Class<?> predictBeanType(Class<?> beanClass, String beanName) {
      if (this.proxyTypes.isEmpty()) {
         return null;
      }

      Object cacheKey = this.getCacheKey(beanClass, beanName);
      return this.proxyTypes.get(cacheKey);
   }

   public Class<?> determineBeanType(Class<?> beanClass, String beanName) {
      Object cacheKey = this.getCacheKey(beanClass, beanName);
      Class<?> proxyType = this.proxyTypes.get(cacheKey);
      if (proxyType == null) {
         TargetSource targetSource = this.getCustomTargetSource(beanClass, beanName);
         if (targetSource != null) {
            if (StringUtils.hasLength(beanName)) {
               this.targetSourcedBeans.add(beanName);
            }
         } else {
            targetSource = EmptyTargetSource.forClass(beanClass);
         }

         Object[] specificInterceptors = this.getAdvicesAndAdvisorsForBean(beanClass, beanName, targetSource);
         if (specificInterceptors != DO_NOT_PROXY) {
            this.advisedBeans.put(cacheKey, Boolean.TRUE);
            proxyType = this.createProxyClass(beanClass, beanName, specificInterceptors, targetSource);
            this.proxyTypes.put(cacheKey, proxyType);
         }
      }

      return proxyType != null ? proxyType : beanClass;
   }

   public Constructor<?> @Nullable [] determineCandidateConstructors(Class<?> beanClass, String beanName) {
      return null;
   }

   public Object getEarlyBeanReference(Object bean, String beanName) {
      Object cacheKey = this.getCacheKey(bean.getClass(), beanName);
      this.earlyBeanReferences.put(cacheKey, bean);
      return this.wrapIfNecessary(bean, beanName, cacheKey);
   }

   public @Nullable Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) {
      Object cacheKey = this.getCacheKey(beanClass, beanName);
      if (!StringUtils.hasLength(beanName) || !this.targetSourcedBeans.contains(beanName)) {
         if (this.advisedBeans.containsKey(cacheKey)) {
            return null;
         }

         if (this.isInfrastructureClass(beanClass) || this.shouldSkip(beanClass, beanName)) {
            this.advisedBeans.put(cacheKey, Boolean.FALSE);
            return null;
         }
      }

      TargetSource targetSource = this.getCustomTargetSource(beanClass, beanName);
      if (targetSource != null) {
         if (StringUtils.hasLength(beanName)) {
            this.targetSourcedBeans.add(beanName);
         }

         Object[] specificInterceptors = this.getAdvicesAndAdvisorsForBean(beanClass, beanName, targetSource);
         Object proxy = this.createProxy(beanClass, beanName, specificInterceptors, targetSource);
         this.proxyTypes.put(cacheKey, proxy.getClass());
         return proxy;
      } else {
         return null;
      }
   }

   public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) {
      return pvs;
   }

   public @Nullable Object postProcessAfterInitialization(@Nullable Object bean, String beanName) {
      if (bean != null) {
         Object cacheKey = this.getCacheKey(bean.getClass(), beanName);
         if (this.earlyBeanReferences.remove(cacheKey) != bean) {
            return this.wrapIfNecessary(bean, beanName, cacheKey);
         }
      }

      return bean;
   }

   protected Object getCacheKey(Class<?> beanClass, @Nullable String beanName) {
      return StringUtils.hasLength(beanName) ? new AbstractAutoProxyCreator.ComposedCacheKey(beanClass, beanName) : beanClass;
   }

   protected Object wrapIfNecessary(Object bean, String beanName, Object cacheKey) {
      if (StringUtils.hasLength(beanName) && this.targetSourcedBeans.contains(beanName)) {
         return bean;
      }

      if (Boolean.FALSE.equals(this.advisedBeans.get(cacheKey))) {
         return bean;
      }

      if (!this.isInfrastructureClass(bean.getClass()) && !this.shouldSkip(bean.getClass(), beanName)) {
         Object[] specificInterceptors = this.getAdvicesAndAdvisorsForBean(bean.getClass(), beanName, null);
         if (specificInterceptors != DO_NOT_PROXY) {
            this.advisedBeans.put(cacheKey, Boolean.TRUE);
            Object proxy = this.createProxy(bean.getClass(), beanName, specificInterceptors, new SingletonTargetSource(bean));
            this.proxyTypes.put(cacheKey, proxy.getClass());
            return proxy;
         } else {
            this.advisedBeans.put(cacheKey, Boolean.FALSE);
            return bean;
         }
      } else {
         this.advisedBeans.put(cacheKey, Boolean.FALSE);
         return bean;
      }
   }

   protected boolean isInfrastructureClass(Class<?> beanClass) {
      boolean retVal = Advice.class.isAssignableFrom(beanClass)
         || Pointcut.class.isAssignableFrom(beanClass)
         || Advisor.class.isAssignableFrom(beanClass)
         || AopInfrastructureBean.class.isAssignableFrom(beanClass);
      if (retVal && this.logger.isTraceEnabled()) {
         this.logger.trace("Did not attempt to auto-proxy infrastructure class [" + beanClass.getName() + "]");
      }

      return retVal;
   }

   protected boolean shouldSkip(Class<?> beanClass, String beanName) {
      return AutoProxyUtils.isOriginalInstance(beanName, beanClass);
   }

   protected @Nullable TargetSource getCustomTargetSource(Class<?> beanClass, String beanName) {
      if (this.customTargetSourceCreators != null && this.beanFactory != null && this.beanFactory.containsBean(beanName)) {
         for (TargetSourceCreator tsc : this.customTargetSourceCreators) {
            TargetSource ts = tsc.getTargetSource(beanClass, beanName);
            if (ts != null) {
               if (this.logger.isTraceEnabled()) {
                  this.logger.trace("TargetSourceCreator [" + tsc + "] found custom TargetSource for bean with name '" + beanName + "'");
               }

               return ts;
            }
         }
      }

      return null;
   }

   protected Object createProxy(Class<?> beanClass, @Nullable String beanName, Object @Nullable [] specificInterceptors, TargetSource targetSource) {
      return this.buildProxy(beanClass, beanName, specificInterceptors, targetSource, false);
   }

   private Class<?> createProxyClass(Class<?> beanClass, @Nullable String beanName, Object @Nullable [] specificInterceptors, TargetSource targetSource) {
      return (Class<?>)this.buildProxy(beanClass, beanName, specificInterceptors, targetSource, true);
   }

   private Object buildProxy(
      Class<?> beanClass, @Nullable String beanName, Object @Nullable [] specificInterceptors, TargetSource targetSource, boolean classOnly
   ) {
      if (this.beanFactory instanceof ConfigurableListableBeanFactory clbf) {
         AutoProxyUtils.exposeTargetClass(clbf, beanName, beanClass);
      }

      ProxyFactory proxyFactory = new ProxyFactory();
      proxyFactory.copyFrom(this);
      proxyFactory.setFrozen(false);
      if (this.shouldProxyTargetClass(beanClass, beanName)) {
         proxyFactory.setProxyTargetClass(true);
      } else {
         Class<?>[] ifcs = this.beanFactory instanceof ConfigurableListableBeanFactory clbf ? AutoProxyUtils.determineExposedInterfaces(clbf, beanName) : null;
         if (ifcs != null) {
            proxyFactory.setProxyTargetClass(false);

            for (Class<?> ifc : ifcs) {
               proxyFactory.addInterface(ifc);
            }
         }

         if (ifcs != null ? ifcs.length == 0 : !proxyFactory.isProxyTargetClass()) {
            this.evaluateProxyInterfaces(beanClass, proxyFactory);
         }
      }

      if (proxyFactory.isProxyTargetClass() && (Proxy.isProxyClass(beanClass) || ClassUtils.isLambdaClass(beanClass))) {
         for (Class<?> ifc : beanClass.getInterfaces()) {
            proxyFactory.addInterface(ifc);
         }
      }

      Advisor[] advisors = this.buildAdvisors(beanName, specificInterceptors);
      proxyFactory.addAdvisors(advisors);
      proxyFactory.setTargetSource(targetSource);
      this.customizeProxyFactory(proxyFactory);
      proxyFactory.setFrozen(this.isFrozen());
      if (this.advisorsPreFiltered()) {
         proxyFactory.setPreFiltered(true);
      }

      ClassLoader classLoader = this.getProxyClassLoader();
      if (classLoader instanceof SmartClassLoader smartClassLoader && classLoader != beanClass.getClassLoader()) {
         classLoader = smartClassLoader.getOriginalClassLoader();
      }

      return classOnly ? proxyFactory.getProxyClass(classLoader) : proxyFactory.getProxy(classLoader);
   }

   protected boolean shouldProxyTargetClass(Class<?> beanClass, @Nullable String beanName) {
      return this.beanFactory instanceof ConfigurableListableBeanFactory clbf && AutoProxyUtils.shouldProxyTargetClass(clbf, beanName);
   }

   protected boolean advisorsPreFiltered() {
      return false;
   }

   protected Advisor[] buildAdvisors(@Nullable String beanName, Object @Nullable [] specificInterceptors) {
      Advisor[] commonInterceptors = this.resolveInterceptorNames();
      List<Object> allInterceptors = new ArrayList<>();
      if (specificInterceptors != null) {
         if (specificInterceptors.length > 0) {
            allInterceptors.addAll(Arrays.asList(specificInterceptors));
         }

         if (commonInterceptors.length > 0) {
            if (this.applyCommonInterceptorsFirst) {
               allInterceptors.addAll(0, Arrays.asList(commonInterceptors));
            } else {
               allInterceptors.addAll(Arrays.asList(commonInterceptors));
            }
         }
      }

      if (this.logger.isTraceEnabled()) {
         int nrOfCommonInterceptors = commonInterceptors.length;
         int nrOfSpecificInterceptors = specificInterceptors != null ? specificInterceptors.length : 0;
         this.logger
            .trace(
               "Creating implicit proxy for bean '"
                  + beanName
                  + "' with "
                  + nrOfCommonInterceptors
                  + " common interceptors and "
                  + nrOfSpecificInterceptors
                  + " specific interceptors"
            );
      }

      Advisor[] advisors = new Advisor[allInterceptors.size()];

      for (int i = 0; i < allInterceptors.size(); i++) {
         advisors[i] = this.advisorAdapterRegistry.wrap(allInterceptors.get(i));
      }

      return advisors;
   }

   private Advisor[] resolveInterceptorNames() {
      BeanFactory bf = this.beanFactory;
      ConfigurableBeanFactory cbf = bf instanceof ConfigurableBeanFactory _cbf ? _cbf : null;
      List<Advisor> advisors = new ArrayList<>();

      for (String beanName : this.interceptorNames) {
         if (cbf == null || !cbf.isCurrentlyInCreation(beanName)) {
            Assert.state(bf != null, "BeanFactory required for resolving interceptor names");
            Object next = bf.getBean(beanName);
            advisors.add(this.advisorAdapterRegistry.wrap(next));
         }
      }

      return advisors.toArray(new Advisor[0]);
   }

   protected void customizeProxyFactory(ProxyFactory proxyFactory) {
   }

   protected abstract Object @Nullable [] getAdvicesAndAdvisorsForBean(Class<?> beanClass, String beanName, @Nullable TargetSource customTargetSource) throws BeansException;

   private record ComposedCacheKey(Class<?> beanClass, String beanName) {
   }
}
