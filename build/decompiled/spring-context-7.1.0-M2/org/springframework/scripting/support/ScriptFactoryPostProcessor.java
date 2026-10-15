package org.springframework.scripting.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.DelegatingIntroductionInterceptor;
import org.springframework.asm.Type;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionValidationException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.cglib.core.Signature;
import org.springframework.cglib.proxy.InterfaceMaker;
import org.springframework.context.ResourceLoaderAware;
import org.springframework.core.Conventions;
import org.springframework.core.Ordered;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scripting.ScriptFactory;
import org.springframework.scripting.ScriptSource;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public class ScriptFactoryPostProcessor
   implements SmartInstantiationAwareBeanPostProcessor,
   BeanClassLoaderAware,
   BeanFactoryAware,
   ResourceLoaderAware,
   DisposableBean,
   Ordered {
   public static final String INLINE_SCRIPT_PREFIX = "inline:";
   public static final String REFRESH_CHECK_DELAY_ATTRIBUTE = Conventions.getQualifiedAttributeName(ScriptFactoryPostProcessor.class, "refreshCheckDelay");
   public static final String PROXY_TARGET_CLASS_ATTRIBUTE = Conventions.getQualifiedAttributeName(ScriptFactoryPostProcessor.class, "proxyTargetClass");
   public static final String LANGUAGE_ATTRIBUTE = Conventions.getQualifiedAttributeName(ScriptFactoryPostProcessor.class, "language");
   private static final String SCRIPT_FACTORY_NAME_PREFIX = "scriptFactory.";
   private static final String SCRIPTED_OBJECT_NAME_PREFIX = "scriptedObject.";
   protected final Log logger = LogFactory.getLog(this.getClass());
   private long defaultRefreshCheckDelay = -1L;
   private boolean defaultProxyTargetClass = false;
   private @Nullable ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();
   private @Nullable ConfigurableBeanFactory beanFactory;
   private ResourceLoader resourceLoader = new DefaultResourceLoader();
   final DefaultListableBeanFactory scriptBeanFactory = new DefaultListableBeanFactory();
   private final Map<String, ScriptSource> scriptSourceCache = new ConcurrentHashMap<>();

   public void setDefaultRefreshCheckDelay(long defaultRefreshCheckDelay) {
      this.defaultRefreshCheckDelay = defaultRefreshCheckDelay;
   }

   public void setDefaultProxyTargetClass(boolean defaultProxyTargetClass) {
      this.defaultProxyTargetClass = defaultProxyTargetClass;
   }

   public void setBeanClassLoader(ClassLoader classLoader) {
      this.beanClassLoader = classLoader;
   }

   public void setBeanFactory(BeanFactory beanFactory) {
      if (beanFactory instanceof ConfigurableBeanFactory cbf) {
         this.beanFactory = cbf;
         this.scriptBeanFactory.setParentBeanFactory(this.beanFactory);
         this.scriptBeanFactory.copyConfigurationFrom(this.beanFactory);
         this.scriptBeanFactory.getBeanPostProcessors().removeIf(AopInfrastructureBean.class::isInstance);
      } else {
         throw new IllegalStateException("ScriptFactoryPostProcessor doesn't work with non-ConfigurableBeanFactory: " + beanFactory.getClass());
      }
   }

   @Override
   public void setResourceLoader(ResourceLoader resourceLoader) {
      this.resourceLoader = resourceLoader;
   }

   public int getOrder() {
      return Integer.MIN_VALUE;
   }

   public @Nullable Class<?> predictBeanType(Class<?> beanClass, String beanName) {
      if (!ScriptFactory.class.isAssignableFrom(beanClass)) {
         return null;
      }

      Assert.state(this.beanFactory != null, "No BeanFactory set");
      BeanDefinition bd = this.beanFactory.getMergedBeanDefinition(beanName);

      try {
         String scriptFactoryBeanName = "scriptFactory." + beanName;
         String scriptedObjectBeanName = "scriptedObject." + beanName;
         this.prepareScriptBeans(bd, scriptFactoryBeanName, scriptedObjectBeanName);
         ScriptFactory scriptFactory = (ScriptFactory)this.scriptBeanFactory.getBean(scriptFactoryBeanName, ScriptFactory.class);
         ScriptSource scriptSource = this.getScriptSource(scriptFactoryBeanName, scriptFactory.getScriptSourceLocator());
         Class<?>[] interfaces = scriptFactory.getScriptInterfaces();
         Class<?> scriptedType = scriptFactory.getScriptedObjectType(scriptSource);
         if (scriptedType != null) {
            return scriptedType;
         }

         if (!ObjectUtils.isEmpty(interfaces)) {
            return interfaces.length == 1 ? interfaces[0] : this.createCompositeInterface(interfaces);
         }

         if (bd.isSingleton()) {
            return this.scriptBeanFactory.getBean(scriptedObjectBeanName).getClass();
         }
      } catch (Exception ex) {
         if (ex instanceof BeanCreationException bce && bce.getMostSpecificCause() instanceof BeanCurrentlyInCreationException) {
            if (this.logger.isTraceEnabled()) {
               this.logger.trace("Could not determine scripted object type for bean '" + beanName + "': " + ex.getMessage());
            }
         } else if (this.logger.isDebugEnabled()) {
            this.logger.debug("Could not determine scripted object type for bean '" + beanName + "'", ex);
         }
      }

      return null;
   }

   public PropertyValues postProcessProperties(PropertyValues pvs, Object bean, String beanName) {
      return pvs;
   }

   public @Nullable Object postProcessBeforeInstantiation(Class<?> beanClass, String beanName) {
      if (!ScriptFactory.class.isAssignableFrom(beanClass)) {
         return null;
      }

      Assert.state(this.beanFactory != null, "No BeanFactory set");
      BeanDefinition bd = this.beanFactory.getMergedBeanDefinition(beanName);
      String scriptFactoryBeanName = "scriptFactory." + beanName;
      String scriptedObjectBeanName = "scriptedObject." + beanName;
      this.prepareScriptBeans(bd, scriptFactoryBeanName, scriptedObjectBeanName);
      ScriptFactory scriptFactory = (ScriptFactory)this.scriptBeanFactory.getBean(scriptFactoryBeanName, ScriptFactory.class);
      ScriptSource scriptSource = this.getScriptSource(scriptFactoryBeanName, scriptFactory.getScriptSourceLocator());
      boolean isFactoryBean = false;

      try {
         Class<?> scriptedObjectType = scriptFactory.getScriptedObjectType(scriptSource);
         if (scriptedObjectType != null) {
            isFactoryBean = FactoryBean.class.isAssignableFrom(scriptedObjectType);
         }
      } catch (Exception ex) {
         throw new BeanCreationException(beanName, "Could not determine scripted object type for " + scriptFactory, ex);
      }

      long refreshCheckDelay = this.resolveRefreshCheckDelay(bd);
      if (refreshCheckDelay < 0L) {
         if (isFactoryBean) {
            scriptedObjectBeanName = "&" + scriptedObjectBeanName;
         }

         return this.scriptBeanFactory.getBean(scriptedObjectBeanName);
      } else {
         Class<?>[] interfaces = scriptFactory.getScriptInterfaces();
         RefreshableScriptTargetSource ts = new RefreshableScriptTargetSource(
            this.scriptBeanFactory, scriptedObjectBeanName, scriptFactory, scriptSource, isFactoryBean
         );
         boolean proxyTargetClass = this.resolveProxyTargetClass(bd);
         String language = (String)bd.getAttribute(LANGUAGE_ATTRIBUTE);
         if (!proxyTargetClass || language != null && language.equals("groovy")) {
            ts.setRefreshCheckDelay(refreshCheckDelay);
            return this.createRefreshableProxy(ts, interfaces, proxyTargetClass);
         } else {
            throw new BeanDefinitionValidationException("Cannot use proxyTargetClass=true with script beans where language is not 'groovy': '" + language + "'");
         }
      }
   }

   protected void prepareScriptBeans(BeanDefinition bd, String scriptFactoryBeanName, String scriptedObjectBeanName) {
      synchronized (this.scriptBeanFactory) {
         if (!this.scriptBeanFactory.containsBeanDefinition(scriptedObjectBeanName)) {
            this.scriptBeanFactory.registerBeanDefinition(scriptFactoryBeanName, this.createScriptFactoryBeanDefinition(bd));
            ScriptFactory scriptFactory = (ScriptFactory)this.scriptBeanFactory.getBean(scriptFactoryBeanName, ScriptFactory.class);
            ScriptSource scriptSource = this.getScriptSource(scriptFactoryBeanName, scriptFactory.getScriptSourceLocator());
            Class<?>[] interfaces = scriptFactory.getScriptInterfaces();
            Class<?>[] scriptedInterfaces = interfaces;
            if (scriptFactory.requiresConfigInterface() && !bd.getPropertyValues().isEmpty()) {
               Class<?> configInterface = this.createConfigInterface(bd, interfaces);
               scriptedInterfaces = (Class<?>[])ObjectUtils.addObjectToArray(interfaces, configInterface);
            }

            BeanDefinition objectBd = this.createScriptedObjectBeanDefinition(bd, scriptFactoryBeanName, scriptSource, scriptedInterfaces);
            long refreshCheckDelay = this.resolveRefreshCheckDelay(bd);
            if (refreshCheckDelay >= 0L) {
               objectBd.setScope("prototype");
            }

            this.scriptBeanFactory.registerBeanDefinition(scriptedObjectBeanName, objectBd);
         }
      }
   }

   protected long resolveRefreshCheckDelay(BeanDefinition beanDefinition) {
      long refreshCheckDelay = this.defaultRefreshCheckDelay;
      Object attributeValue = beanDefinition.getAttribute(REFRESH_CHECK_DELAY_ATTRIBUTE);
      if (attributeValue instanceof Number number) {
         refreshCheckDelay = number.longValue();
      } else if (attributeValue instanceof String text) {
         refreshCheckDelay = Long.parseLong(text);
      } else if (attributeValue != null) {
         throw new BeanDefinitionStoreException(
            "Invalid refresh check delay attribute ["
               + REFRESH_CHECK_DELAY_ATTRIBUTE
               + "] with value '"
               + attributeValue
               + "': needs to be of type Number or String"
         );
      }

      return refreshCheckDelay;
   }

   protected boolean resolveProxyTargetClass(BeanDefinition beanDefinition) {
      boolean proxyTargetClass = this.defaultProxyTargetClass;
      Object attributeValue = beanDefinition.getAttribute(PROXY_TARGET_CLASS_ATTRIBUTE);
      if (attributeValue instanceof Boolean boo) {
         proxyTargetClass = boo;
      } else if (attributeValue instanceof String text) {
         proxyTargetClass = Boolean.parseBoolean(text);
      } else if (attributeValue != null) {
         throw new BeanDefinitionStoreException(
            "Invalid proxy target class attribute ["
               + PROXY_TARGET_CLASS_ATTRIBUTE
               + "] with value '"
               + attributeValue
               + "': needs to be of type Boolean or String"
         );
      }

      return proxyTargetClass;
   }

   protected BeanDefinition createScriptFactoryBeanDefinition(BeanDefinition bd) {
      GenericBeanDefinition scriptBd = new GenericBeanDefinition();
      scriptBd.setBeanClassName(bd.getBeanClassName());
      scriptBd.getConstructorArgumentValues().addArgumentValues(bd.getConstructorArgumentValues());
      return scriptBd;
   }

   protected ScriptSource getScriptSource(String beanName, String scriptSourceLocator) {
      return this.scriptSourceCache.computeIfAbsent(beanName, key -> this.convertToScriptSource(beanName, scriptSourceLocator, this.resourceLoader));
   }

   protected ScriptSource convertToScriptSource(String beanName, String scriptSourceLocator, ResourceLoader resourceLoader) {
      return scriptSourceLocator.startsWith("inline:")
         ? new StaticScriptSource(scriptSourceLocator.substring("inline:".length()), beanName)
         : new ResourceScriptSource(resourceLoader.getResource(scriptSourceLocator));
   }

   protected Class<?> createConfigInterface(BeanDefinition bd, Class<?> @Nullable [] interfaces) {
      InterfaceMaker maker = new InterfaceMaker();
      PropertyValue[] pvs = bd.getPropertyValues().getPropertyValues();

      for (PropertyValue pv : pvs) {
         String propertyName = pv.getName();
         Class<?> propertyType = BeanUtils.findPropertyType(propertyName, interfaces);
         String setterName = "set" + StringUtils.capitalize(propertyName);
         Signature signature = new Signature(setterName, Type.VOID_TYPE, new Type[]{Type.getType(propertyType)});
         maker.add(signature, new Type[0]);
      }

      if (StringUtils.hasText(bd.getInitMethodName())) {
         Signature signature = new Signature(bd.getInitMethodName(), Type.VOID_TYPE, new Type[0]);
         maker.add(signature, new Type[0]);
      }

      if (StringUtils.hasText(bd.getDestroyMethodName())) {
         Signature signature = new Signature(bd.getDestroyMethodName(), Type.VOID_TYPE, new Type[0]);
         maker.add(signature, new Type[0]);
      }

      return maker.create();
   }

   protected Class<?> createCompositeInterface(Class<?>[] interfaces) {
      return ClassUtils.createCompositeInterface(interfaces, this.beanClassLoader);
   }

   protected BeanDefinition createScriptedObjectBeanDefinition(
      BeanDefinition bd, String scriptFactoryBeanName, ScriptSource scriptSource, Class<?> @Nullable [] interfaces
   ) {
      GenericBeanDefinition objectBd = new GenericBeanDefinition(bd);
      objectBd.setFactoryBeanName(scriptFactoryBeanName);
      objectBd.setFactoryMethodName("getScriptedObject");
      objectBd.getConstructorArgumentValues().clear();
      objectBd.getConstructorArgumentValues().addIndexedArgumentValue(0, scriptSource);
      objectBd.getConstructorArgumentValues().addIndexedArgumentValue(1, interfaces);
      return objectBd;
   }

   protected Object createRefreshableProxy(TargetSource ts, Class<?> @Nullable [] interfaces, boolean proxyTargetClass) {
      ProxyFactory proxyFactory = new ProxyFactory();
      proxyFactory.setTargetSource(ts);
      ClassLoader classLoader = this.beanClassLoader;
      if (interfaces != null) {
         proxyFactory.setInterfaces(interfaces);
      } else {
         Class<?> targetClass = ts.getTargetClass();
         if (targetClass != null) {
            proxyFactory.setInterfaces(ClassUtils.getAllInterfacesForClass(targetClass, this.beanClassLoader));
         }
      }

      if (proxyTargetClass) {
         classLoader = null;
         proxyFactory.setProxyTargetClass(true);
      }

      DelegatingIntroductionInterceptor introduction = new DelegatingIntroductionInterceptor(ts);
      introduction.suppressInterface(TargetSource.class);
      proxyFactory.addAdvice(introduction);
      return proxyFactory.getProxy(classLoader);
   }

   public void destroy() {
      this.scriptBeanFactory.destroySingletons();
   }
}
