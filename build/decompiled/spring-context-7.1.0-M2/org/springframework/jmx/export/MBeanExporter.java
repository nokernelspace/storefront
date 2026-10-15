package org.springframework.jmx.export;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import javax.management.DynamicMBean;
import javax.management.JMException;
import javax.management.MBeanException;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.management.StandardMBean;
import javax.management.modelmbean.ModelMBean;
import javax.management.modelmbean.ModelMBeanInfo;
import javax.management.modelmbean.RequiredModelMBean;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.target.LazyInitTargetSource;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.CannotLoadBeanClassException;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.jmx.export.assembler.AutodetectCapableMBeanInfoAssembler;
import org.springframework.jmx.export.assembler.MBeanInfoAssembler;
import org.springframework.jmx.export.assembler.SimpleReflectiveMBeanInfoAssembler;
import org.springframework.jmx.export.naming.KeyNamingStrategy;
import org.springframework.jmx.export.naming.ObjectNamingStrategy;
import org.springframework.jmx.export.naming.SelfNaming;
import org.springframework.jmx.export.notification.ModelMBeanNotificationPublisher;
import org.springframework.jmx.export.notification.NotificationPublisherAware;
import org.springframework.jmx.support.JmxUtils;
import org.springframework.jmx.support.MBeanRegistrationSupport;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

public class MBeanExporter
   extends MBeanRegistrationSupport
   implements MBeanExportOperations,
   BeanClassLoaderAware,
   BeanFactoryAware,
   InitializingBean,
   SmartInitializingSingleton,
   DisposableBean {
   @Deprecated(since = "6.1")
   public static final int AUTODETECT_NONE = 0;
   @Deprecated(since = "6.1")
   public static final int AUTODETECT_MBEAN = 1;
   @Deprecated(since = "6.1")
   public static final int AUTODETECT_ASSEMBLER = 2;
   @Deprecated(since = "6.1")
   public static final int AUTODETECT_ALL = 3;
   private static final String WILDCARD = "*";
   private static final String MR_TYPE_OBJECT_REFERENCE = "ObjectReference";
   private static final Map<String, Integer> constants = Map.of("AUTODETECT_NONE", 0, "AUTODETECT_MBEAN", 1, "AUTODETECT_ASSEMBLER", 2, "AUTODETECT_ALL", 3);
   private @Nullable Map<String, Object> beans;
   @Nullable Integer autodetectMode;
   private boolean allowEagerInit = false;
   private MBeanInfoAssembler assembler = new SimpleReflectiveMBeanInfoAssembler();
   private ObjectNamingStrategy namingStrategy = new KeyNamingStrategy();
   private boolean ensureUniqueRuntimeObjectNames = true;
   private boolean exposeManagedResourceClassLoader = true;
   private final Set<String> excludedBeans = new HashSet<>();
   private MBeanExporterListener @Nullable [] listeners;
   private NotificationListenerBean @Nullable [] notificationListeners;
   private final Map<NotificationListenerBean, ObjectName[]> registeredNotificationListeners = new LinkedHashMap<>();
   private @Nullable ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();
   private @Nullable ListableBeanFactory beanFactory;

   public void setBeans(Map<String, Object> beans) {
      this.beans = beans;
   }

   public void setAutodetect(boolean autodetect) {
      this.autodetectMode = autodetect ? 3 : 0;
   }

   @Deprecated(since = "6.1")
   public void setAutodetectModeName(String constantName) {
      Assert.hasText(constantName, "'constantName' must not be null or blank");
      Integer mode = constants.get(constantName);
      Assert.notNull(mode, "Only autodetect constants allowed");
      this.autodetectMode = mode;
   }

   @Deprecated(since = "6.1")
   public void setAutodetectMode(int autodetectMode) {
      Assert.isTrue(constants.containsValue(autodetectMode), "Only values of autodetect constants allowed");
      this.autodetectMode = autodetectMode;
   }

   public void setAllowEagerInit(boolean allowEagerInit) {
      this.allowEagerInit = allowEagerInit;
   }

   public void setAssembler(MBeanInfoAssembler assembler) {
      this.assembler = assembler;
   }

   public void setNamingStrategy(ObjectNamingStrategy namingStrategy) {
      this.namingStrategy = namingStrategy;
   }

   public void setEnsureUniqueRuntimeObjectNames(boolean ensureUniqueRuntimeObjectNames) {
      this.ensureUniqueRuntimeObjectNames = ensureUniqueRuntimeObjectNames;
   }

   public void setExposeManagedResourceClassLoader(boolean exposeManagedResourceClassLoader) {
      this.exposeManagedResourceClassLoader = exposeManagedResourceClassLoader;
   }

   public void setExcludedBeans(String... excludedBeans) {
      this.excludedBeans.clear();
      Collections.addAll(this.excludedBeans, excludedBeans);
   }

   public void addExcludedBean(String excludedBean) {
      Assert.notNull(excludedBean, "ExcludedBean must not be null");
      this.excludedBeans.add(excludedBean);
   }

   public void setListeners(MBeanExporterListener... listeners) {
      this.listeners = listeners;
   }

   public void setNotificationListeners(NotificationListenerBean... notificationListeners) {
      this.notificationListeners = notificationListeners;
   }

   public void setNotificationListenerMappings(Map<?, ? extends NotificationListener> listeners) {
      Assert.notNull(listeners, "'listeners' must not be null");
      List<NotificationListenerBean> notificationListeners = new ArrayList<>(listeners.size());
      listeners.forEach((key, listener) -> {
         NotificationListenerBean bean = new NotificationListenerBean(listener);
         if (key != null && !"*".equals(key)) {
            bean.setMappedObjectName(key);
         }

         notificationListeners.add(bean);
      });
      this.notificationListeners = notificationListeners.toArray(new NotificationListenerBean[0]);
   }

   public void setBeanClassLoader(ClassLoader classLoader) {
      this.beanClassLoader = classLoader;
   }

   public void setBeanFactory(BeanFactory beanFactory) {
      if (beanFactory instanceof ListableBeanFactory lbf) {
         this.beanFactory = lbf;
      } else {
         this.logger.debug("MBeanExporter not running in a ListableBeanFactory: auto-detection of MBeans not available.");
      }
   }

   public void afterPropertiesSet() {
      if (this.server == null) {
         this.server = JmxUtils.locateMBeanServer();
      }
   }

   public void afterSingletonsInstantiated() {
      try {
         this.logger.debug("Registering beans for JMX exposure on startup");
         this.registerBeans();
         this.registerNotificationListeners();
      } catch (RuntimeException ex) {
         this.unregisterNotificationListeners();
         this.unregisterBeans();
         throw ex;
      }
   }

   public void destroy() {
      this.logger.debug("Unregistering JMX-exposed beans on shutdown");
      this.unregisterNotificationListeners();
      this.unregisterBeans();
   }

   @Override
   public ObjectName registerManagedResource(Object managedResource) throws MBeanExportException {
      return this.registerManagedResource(managedResource, (String)null);
   }

   public ObjectName registerManagedResource(Object managedResource, @Nullable String beanKey) throws MBeanExportException {
      Assert.notNull(managedResource, "Managed resource must not be null");

      ObjectName objectName;
      try {
         objectName = this.getObjectName(managedResource, beanKey);
         if (beanKey == null && this.ensureUniqueRuntimeObjectNames) {
            objectName = JmxUtils.appendIdentityToObjectName(objectName, managedResource);
         }
      } catch (Throwable ex) {
         throw new MBeanExportException("Unable to generate ObjectName for MBean [" + managedResource + "]", ex);
      }

      this.registerManagedResource(managedResource, objectName);
      return objectName;
   }

   @Override
   public void registerManagedResource(Object managedResource, ObjectName objectName) throws MBeanExportException {
      Assert.notNull(managedResource, "Managed resource must not be null");
      Assert.notNull(objectName, "ObjectName must not be null");

      try {
         if (this.isMBean(managedResource.getClass())) {
            this.doRegister(managedResource, objectName);
         } else {
            ModelMBean mbean = this.createAndConfigureMBean(managedResource, managedResource.getClass().getName());
            this.doRegister(mbean, objectName);
            this.injectNotificationPublisherIfNecessary(managedResource, mbean, objectName);
         }
      } catch (JMException ex) {
         throw new UnableToRegisterMBeanException("Unable to register MBean [" + managedResource + "] with object name [" + objectName + "]", ex);
      }
   }

   @Override
   public void unregisterManagedResource(ObjectName objectName) {
      Assert.notNull(objectName, "ObjectName must not be null");
      this.doUnregister(objectName);
   }

   protected void registerBeans() {
      if (this.beans == null) {
         this.beans = new HashMap<>();
         if (this.autodetectMode == null) {
            this.autodetectMode = 3;
         }
      }

      int mode = this.autodetectMode != null ? this.autodetectMode : 0;
      if (mode != 0) {
         if (this.beanFactory == null) {
            throw new MBeanExportException("Cannot autodetect MBeans if not running in a BeanFactory");
         }

         if (mode == 1 || mode == 3) {
            this.logger.debug("Auto-detecting user-defined JMX MBeans");
            this.autodetect(this.beans, (beanClass, beanName) -> this.isMBean(beanClass));
         }

         if ((mode == 2 || mode == 3) && this.assembler instanceof AutodetectCapableMBeanInfoAssembler autodetectCapableAssembler) {
            this.autodetect(this.beans, autodetectCapableAssembler::includeBean);
         }
      }

      if (!this.beans.isEmpty()) {
         this.beans.forEach((beanName, instance) -> this.registerBeanNameOrInstance(instance, beanName));
      }
   }

   protected boolean isBeanDefinitionLazyInit(ListableBeanFactory beanFactory, String beanName) {
      return beanFactory instanceof ConfigurableListableBeanFactory clbf
         && beanFactory.containsBeanDefinition(beanName)
         && clbf.getBeanDefinition(beanName).isLazyInit();
   }

   protected ObjectName registerBeanNameOrInstance(Object mapValue, String beanKey) throws MBeanExportException {
      try {
         if (mapValue instanceof String beanName) {
            if (this.beanFactory == null) {
               throw new MBeanExportException("Cannot resolve bean names if not running in a BeanFactory");
            } else if (this.isBeanDefinitionLazyInit(this.beanFactory, beanName)) {
               ObjectName objectName = this.registerLazyInit(beanName, beanKey);
               this.replaceNotificationListenerBeanNameKeysIfNecessary(beanName, objectName);
               return objectName;
            } else {
               Object bean = this.beanFactory.getBean(beanName);
               ObjectName objectName = this.registerBeanInstance(bean, beanKey);
               this.replaceNotificationListenerBeanNameKeysIfNecessary(beanName, objectName);
               return objectName;
            }
         } else {
            if (this.beanFactory != null) {
               Map<String, ?> beansOfSameType = this.beanFactory.getBeansOfType(mapValue.getClass(), false, this.allowEagerInit);

               for (Entry<String, ?> entry : beansOfSameType.entrySet()) {
                  if (entry.getValue() == mapValue) {
                     String beanName = entry.getKey();
                     ObjectName objectName = this.registerBeanInstance(mapValue, beanKey);
                     this.replaceNotificationListenerBeanNameKeysIfNecessary(beanName, objectName);
                     return objectName;
                  }
               }
            }

            return this.registerBeanInstance(mapValue, beanKey);
         }
      } catch (Throwable ex) {
         throw new UnableToRegisterMBeanException("Unable to register MBean [" + mapValue + "] with key '" + beanKey + "'", ex);
      }
   }

   private void replaceNotificationListenerBeanNameKeysIfNecessary(String beanName, ObjectName objectName) {
      if (this.notificationListeners != null) {
         for (NotificationListenerBean notificationListener : this.notificationListeners) {
            notificationListener.replaceObjectName(beanName, objectName);
         }
      }
   }

   private ObjectName registerBeanInstance(Object bean, String beanKey) throws JMException {
      ObjectName objectName = this.getObjectName(bean, beanKey);
      Object mbeanToExpose = null;
      if (this.isMBean(bean.getClass())) {
         mbeanToExpose = bean;
      } else {
         DynamicMBean adaptedBean = this.adaptMBeanIfPossible(bean);
         if (adaptedBean != null) {
            mbeanToExpose = adaptedBean;
         }
      }

      if (mbeanToExpose != null) {
         if (this.logger.isDebugEnabled()) {
            this.logger.debug("Located MBean '" + beanKey + "': registering with JMX server as MBean [" + objectName + "]");
         }

         this.doRegister(mbeanToExpose, objectName);
      } else {
         if (this.logger.isDebugEnabled()) {
            this.logger.debug("Located managed bean '" + beanKey + "': registering with JMX server as MBean [" + objectName + "]");
         }

         ModelMBean mbean = this.createAndConfigureMBean(bean, beanKey);
         this.doRegister(mbean, objectName);
         this.injectNotificationPublisherIfNecessary(bean, mbean, objectName);
      }

      return objectName;
   }

   private ObjectName registerLazyInit(String beanName, String beanKey) throws JMException {
      Assert.state(this.beanFactory != null, "No BeanFactory set");
      ProxyFactory proxyFactory = new ProxyFactory();
      proxyFactory.setProxyTargetClass(true);
      proxyFactory.setFrozen(true);
      if (this.isMBean(this.beanFactory.getType(beanName))) {
         LazyInitTargetSource targetSource = new LazyInitTargetSource();
         targetSource.setTargetBeanName(beanName);
         targetSource.setBeanFactory(this.beanFactory);
         proxyFactory.setTargetSource(targetSource);
         Object proxy = proxyFactory.getProxy(this.beanClassLoader);
         ObjectName objectName = this.getObjectName(proxy, beanKey);
         if (this.logger.isDebugEnabled()) {
            this.logger.debug("Located MBean '" + beanKey + "': registering with JMX server as lazy-init MBean [" + objectName + "]");
         }

         this.doRegister(proxy, objectName);
         return objectName;
      } else {
         MBeanExporter.NotificationPublisherAwareLazyTargetSource targetSource = new MBeanExporter.NotificationPublisherAwareLazyTargetSource();
         targetSource.setTargetBeanName(beanName);
         targetSource.setBeanFactory(this.beanFactory);
         proxyFactory.setTargetSource(targetSource);
         Object proxy = proxyFactory.getProxy(this.beanClassLoader);
         ObjectName objectName = this.getObjectName(proxy, beanKey);
         if (this.logger.isDebugEnabled()) {
            this.logger.debug("Located simple bean '" + beanKey + "': registering with JMX server as lazy-init MBean [" + objectName + "]");
         }

         ModelMBean mbean = this.createAndConfigureMBean(proxy, beanKey);
         targetSource.setModelMBean(mbean);
         targetSource.setObjectName(objectName);
         this.doRegister(mbean, objectName);
         return objectName;
      }
   }

   protected ObjectName getObjectName(Object bean, @Nullable String beanKey) throws MalformedObjectNameException {
      return bean instanceof SelfNaming selfNaming ? selfNaming.getObjectName() : this.namingStrategy.getObjectName(bean, beanKey);
   }

   protected boolean isMBean(@Nullable Class<?> beanClass) {
      return JmxUtils.isMBean(beanClass);
   }

   protected @Nullable DynamicMBean adaptMBeanIfPossible(Object bean) throws JMException {
      Class<?> targetClass = AopUtils.getTargetClass(bean);
      if (targetClass != bean.getClass()) {
         Class<?> ifc = JmxUtils.getMXBeanInterface(targetClass);
         if (ifc != null) {
            if (!ifc.isInstance(bean)) {
               throw new NotCompliantMBeanException(
                  "Managed bean [" + bean + "] has a target class with an MXBean interface but does not expose it in the proxy"
               );
            }

            return new StandardMBean(bean, (Class<Object>)ifc, true);
         }

         ifc = JmxUtils.getMBeanInterface(targetClass);
         if (ifc != null) {
            if (!ifc.isInstance(bean)) {
               throw new NotCompliantMBeanException(
                  "Managed bean [" + bean + "] has a target class with an MBean interface but does not expose it in the proxy"
               );
            }

            return new StandardMBean(bean, (Class<Object>)ifc);
         }
      }

      return null;
   }

   protected ModelMBean createAndConfigureMBean(Object managedResource, String beanKey) throws MBeanExportException {
      try {
         ModelMBean mbean = this.createModelMBean();
         mbean.setModelMBeanInfo(this.getMBeanInfo(managedResource, beanKey));
         mbean.setManagedResource(managedResource, "ObjectReference");
         return mbean;
      } catch (Throwable ex) {
         throw new MBeanExportException("Could not create ModelMBean for managed resource [" + managedResource + "] with key '" + beanKey + "'", ex);
      }
   }

   protected ModelMBean createModelMBean() throws MBeanException {
      return this.exposeManagedResourceClassLoader ? new SpringModelMBean() : new RequiredModelMBean();
   }

   private ModelMBeanInfo getMBeanInfo(Object managedBean, String beanKey) throws JMException {
      ModelMBeanInfo info = this.assembler.getMBeanInfo(managedBean, beanKey);
      if (this.logger.isInfoEnabled() && ObjectUtils.isEmpty(info.getAttributes()) && ObjectUtils.isEmpty(info.getOperations())) {
         this.logger.info("Bean with key '" + beanKey + "' has been registered as an MBean but has no exposed attributes or operations");
      }

      return info;
   }

   private void autodetect(Map<String, Object> beans, MBeanExporter.AutodetectCallback callback) {
      Assert.state(this.beanFactory != null, "No BeanFactory set");
      Set<String> beanNames = CollectionUtils.newLinkedHashSet(this.beanFactory.getBeanDefinitionCount());
      Collections.addAll(beanNames, this.beanFactory.getBeanDefinitionNames());
      if (this.beanFactory instanceof ConfigurableBeanFactory cbf) {
         Collections.addAll(beanNames, cbf.getSingletonNames());
      }

      for (String beanName : beanNames) {
         if (!this.isExcluded(beanName) && !this.isBeanDefinitionAbstract(this.beanFactory, beanName)) {
            try {
               Class<?> beanClass = this.beanFactory.getType(beanName);
               if (beanClass != null && callback.include(beanClass, beanName)) {
                  boolean lazyInit = this.isBeanDefinitionLazyInit(this.beanFactory, beanName);
                  Object beanInstance = null;
                  if (!lazyInit) {
                     beanInstance = this.beanFactory.getBean(beanName);
                     if (!beanClass.isInstance(beanInstance)) {
                        continue;
                     }
                  }

                  if (!ScopedProxyUtils.isScopedTarget(beanName)
                     && !beans.containsValue(beanName)
                     && (beanInstance == null || !CollectionUtils.containsInstance(beans.values(), beanInstance))) {
                     beans.put(beanName, beanInstance != null ? beanInstance : beanName);
                     if (this.logger.isDebugEnabled()) {
                        this.logger.debug("Bean with name '" + beanName + "' has been autodetected for JMX exposure");
                     }
                  } else if (this.logger.isTraceEnabled()) {
                     this.logger.trace("Bean with name '" + beanName + "' is already registered for JMX exposure");
                  }
               }
            } catch (CannotLoadBeanClassException ex) {
               if (this.allowEagerInit) {
                  throw ex;
               }
            }
         }
      }
   }

   private boolean isExcluded(String beanName) {
      return this.excludedBeans.contains(beanName) || !beanName.isEmpty() && beanName.charAt(0) == '&' && this.excludedBeans.contains(beanName.substring(1));
   }

   private boolean isBeanDefinitionAbstract(ListableBeanFactory beanFactory, String beanName) {
      return beanFactory instanceof ConfigurableListableBeanFactory clbf
         && beanFactory.containsBeanDefinition(beanName)
         && clbf.getBeanDefinition(beanName).isAbstract();
   }

   private void injectNotificationPublisherIfNecessary(Object managedResource, @Nullable ModelMBean modelMBean, @Nullable ObjectName objectName) {
      if (managedResource instanceof NotificationPublisherAware npa && modelMBean != null && objectName != null) {
         npa.setNotificationPublisher(new ModelMBeanNotificationPublisher(modelMBean, objectName, managedResource));
      }
   }

   private void registerNotificationListeners() throws MBeanExportException {
      if (this.notificationListeners != null) {
         Assert.state(this.server != null, "No MBeanServer available");

         for (NotificationListenerBean bean : this.notificationListeners) {
            try {
               ObjectName[] mappedObjectNames = bean.getResolvedObjectNames();
               if (mappedObjectNames == null) {
                  mappedObjectNames = this.getRegisteredObjectNames();
               }

               if (this.registeredNotificationListeners.put(bean, mappedObjectNames) == null) {
                  for (ObjectName mappedObjectName : mappedObjectNames) {
                     this.server.addNotificationListener(mappedObjectName, bean.getNotificationListener(), bean.getNotificationFilter(), bean.getHandback());
                  }
               }
            } catch (Throwable ex) {
               throw new MBeanExportException("Unable to register NotificationListener", ex);
            }
         }
      }
   }

   private void unregisterNotificationListeners() {
      if (this.server != null) {
         this.registeredNotificationListeners.forEach((bean, mappedObjectNames) -> {
            for (ObjectName mappedObjectName : mappedObjectNames) {
               try {
                  this.server.removeNotificationListener(mappedObjectName, bean.getNotificationListener(), bean.getNotificationFilter(), bean.getHandback());
               } catch (Throwable ex) {
                  if (this.logger.isDebugEnabled()) {
                     this.logger.debug("Unable to unregister NotificationListener", ex);
                  }
               }
            }
         });
      }

      this.registeredNotificationListeners.clear();
   }

   @Override
   protected void onRegister(ObjectName objectName) {
      this.notifyListenersOfRegistration(objectName);
   }

   @Override
   protected void onUnregister(ObjectName objectName) {
      this.notifyListenersOfUnregistration(objectName);
   }

   private void notifyListenersOfRegistration(ObjectName objectName) {
      if (this.listeners != null) {
         for (MBeanExporterListener listener : this.listeners) {
            listener.mbeanRegistered(objectName);
         }
      }
   }

   private void notifyListenersOfUnregistration(ObjectName objectName) {
      if (this.listeners != null) {
         for (MBeanExporterListener listener : this.listeners) {
            listener.mbeanUnregistered(objectName);
         }
      }
   }

   @FunctionalInterface
   private interface AutodetectCallback {
      boolean include(Class<?> beanClass, String beanName);
   }

   private class NotificationPublisherAwareLazyTargetSource extends LazyInitTargetSource {
      private @Nullable ModelMBean modelMBean;
      private @Nullable ObjectName objectName;

      public void setModelMBean(ModelMBean modelMBean) {
         this.modelMBean = modelMBean;
      }

      public void setObjectName(ObjectName objectName) {
         this.objectName = objectName;
      }

      public Object getTarget() {
         try {
            return super.getTarget();
         } catch (RuntimeException ex) {
            if (this.logger.isInfoEnabled()) {
               this.logger.info("Failed to retrieve target for JMX-exposed bean [" + this.objectName + "]: " + ex);
            }

            throw ex;
         }
      }

      protected void postProcessTargetObject(Object targetObject) {
         MBeanExporter.this.injectNotificationPublisherIfNecessary(targetObject, this.modelMBean, this.objectName);
      }
   }
}
