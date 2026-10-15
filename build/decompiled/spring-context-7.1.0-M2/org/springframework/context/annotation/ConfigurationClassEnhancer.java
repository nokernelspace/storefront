package org.springframework.context.annotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.scope.ScopedProxyFactoryBean;
import org.springframework.aot.AotDetector;
import org.springframework.asm.Type;
import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.support.SimpleInstantiationStrategy;
import org.springframework.cglib.core.ClassGenerator;
import org.springframework.cglib.core.ClassLoaderAwareGeneratorStrategy;
import org.springframework.cglib.core.CodeGenerationException;
import org.springframework.cglib.core.SpringNamingPolicy;
import org.springframework.cglib.proxy.Callback;
import org.springframework.cglib.proxy.CallbackFilter;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.Factory;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.cglib.proxy.NoOp;
import org.springframework.cglib.transform.ClassEmitterTransformer;
import org.springframework.cglib.transform.TransformingClassGenerator;
import org.springframework.core.SmartClassLoader;
import org.springframework.objenesis.ObjenesisException;
import org.springframework.objenesis.SpringObjenesis;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.ReflectionUtils;

class ConfigurationClassEnhancer {
   static final Callback[] CALLBACKS = new Callback[]{
      new ConfigurationClassEnhancer.BeanMethodInterceptor(), new ConfigurationClassEnhancer.BeanFactoryAwareMethodInterceptor(), NoOp.INSTANCE
   };
   private static final ConfigurationClassEnhancer.ConditionalCallbackFilter CALLBACK_FILTER = new ConfigurationClassEnhancer.ConditionalCallbackFilter(
      CALLBACKS
   );
   private static final String BEAN_FACTORY_FIELD = "$$beanFactory";
   private static final Log logger = LogFactory.getLog(ConfigurationClassEnhancer.class);
   private static final SpringObjenesis objenesis = new SpringObjenesis();

   public Class<?> enhance(Class<?> configClass, @Nullable ClassLoader classLoader) {
      if (ConfigurationClassEnhancer.EnhancedConfiguration.class.isAssignableFrom(configClass)) {
         if (logger.isDebugEnabled()) {
            logger.debug(
               String.format(
                  "Ignoring request to enhance %s as it has already been enhanced. This usually indicates that more than one ConfigurationClassPostProcessor has been registered (for example, via <context:annotation-config>). This is harmless, but you may want check your configuration and remove one CCPP if possible",
                  configClass.getName()
               )
            );
         }

         return configClass;
      } else {
         try {
            boolean classLoaderMismatch = classLoader != null && classLoader != configClass.getClassLoader();
            if (classLoaderMismatch && classLoader instanceof SmartClassLoader smartClassLoader) {
               classLoader = smartClassLoader.getOriginalClassLoader();
               classLoaderMismatch = classLoader != configClass.getClassLoader();
            }

            if (classLoaderMismatch && this.reliesOnPackageVisibility(configClass)) {
               classLoader = configClass.getClassLoader();
               classLoaderMismatch = false;
            }

            Enhancer enhancer = this.newEnhancer(configClass, classLoader);
            Class<?> enhancedClass = this.createClass(enhancer, classLoaderMismatch);
            if (logger.isTraceEnabled()) {
               logger.trace(String.format("Successfully enhanced %s; enhanced class name is: %s", configClass.getName(), enhancedClass.getName()));
            }

            return enhancedClass;
         } catch (CodeGenerationException ex) {
            throw new BeanDefinitionStoreException(
               "Could not enhance configuration class ["
                  + configClass.getName()
                  + "]. Consider declaring @Configuration(proxyBeanMethods=false) without inter-bean references between @Bean methods on the configuration class, avoiding the need for CGLIB enhancement.",
               ex
            );
         }
      }
   }

   private boolean reliesOnPackageVisibility(Class<?> configSuperClass) {
      int mod = configSuperClass.getModifiers();
      if (!Modifier.isPublic(mod) && !Modifier.isProtected(mod)) {
         return true;
      }

      for (Constructor<?> ctor : configSuperClass.getDeclaredConstructors()) {
         mod = ctor.getModifiers();
         if (!Modifier.isPublic(mod) && !Modifier.isProtected(mod)) {
            return true;
         }
      }

      for (Method method : ReflectionUtils.getDeclaredMethods(configSuperClass)) {
         if (BeanAnnotationHelper.isBeanAnnotated(method)) {
            mod = method.getModifiers();
            if (!Modifier.isPublic(mod) && !Modifier.isProtected(mod)) {
               return true;
            }
         }
      }

      return false;
   }

   private Enhancer newEnhancer(Class<?> configSuperClass, @Nullable ClassLoader classLoader) {
      Enhancer enhancer = new Enhancer();
      if (classLoader != null) {
         enhancer.setClassLoader(classLoader);
         if (classLoader instanceof SmartClassLoader smartClassLoader && smartClassLoader.isClassReloadable(configSuperClass)) {
            enhancer.setUseCache(false);
         }
      }

      enhancer.setSuperclass(configSuperClass);
      enhancer.setInterfaces(new Class[]{ConfigurationClassEnhancer.EnhancedConfiguration.class});
      enhancer.setUseFactory(false);
      enhancer.setNamingPolicy(SpringNamingPolicy.INSTANCE);
      enhancer.setAttemptLoad(enhancer.getUseCache() && AotDetector.useGeneratedArtifacts());
      enhancer.setStrategy(new ConfigurationClassEnhancer.BeanFactoryAwareGeneratorStrategy(classLoader));
      enhancer.setCallbackFilter(CALLBACK_FILTER);
      enhancer.setCallbackTypes(CALLBACK_FILTER.getCallbackTypes());
      return enhancer;
   }

   private Class<?> createClass(Enhancer enhancer, boolean fallback) {
      // $VF: Couldn't be decompiled
      // Please report this to the Vineflower issue tracker, at https://github.com/Vineflower/vineflower/issues with a copy of the class file (if you have the rights to distribute it!)
      // java.lang.IndexOutOfBoundsException: Index 1 out of bounds for length 1
      //   at java.base/jdk.internal.util.Preconditions.outOfBounds(Preconditions.java:100)
      //   at java.base/jdk.internal.util.Preconditions.outOfBoundsCheckIndex(Preconditions.java:106)
      //   at java.base/jdk.internal.util.Preconditions.checkIndex(Preconditions.java:302)
      //   at java.base/java.util.Objects.checkIndex(Objects.java:385)
      //   at java.base/java.util.ArrayList.get(ArrayList.java:427)
      //   at org.jetbrains.java.decompiler.modules.decompiler.TryWithResourcesProcessor.makeTryWithResourceJ11(TryWithResourcesProcessor.java:166)
      //   at org.jetbrains.java.decompiler.modules.decompiler.TryHelper.makeTryWithResourceRec(TryHelper.java:33)
      //   at org.jetbrains.java.decompiler.modules.decompiler.TryHelper.makeTryWithResourceRec(TryHelper.java:43)
      //   at org.jetbrains.java.decompiler.modules.decompiler.TryHelper.makeTryWithResourceRec(TryHelper.java:43)
      //   at org.jetbrains.java.decompiler.modules.decompiler.TryHelper.enhanceTryStats(TryHelper.java:14)
      //   at org.jetbrains.java.decompiler.main.rels.MethodProcessor.codeToJava(MethodProcessor.java:363)
      //
      // Bytecode:
      // 00: aload 1
      // 01: invokevirtual org/springframework/cglib/proxy/Enhancer.createClass ()Ljava/lang/Class;
      // 04: astore 3
      // 05: goto 36
      // 08: astore 4
      // 0a: iload 2
      // 0b: ifne 2c
      // 0e: aload 4
      // 10: instanceof org/springframework/cglib/core/CodeGenerationException
      // 13: ifeq 22
      // 16: aload 4
      // 18: checkcast org/springframework/cglib/core/CodeGenerationException
      // 1b: astore 5
      // 1d: aload 5
      // 1f: goto 2b
      // 22: new org/springframework/cglib/core/CodeGenerationException
      // 25: dup
      // 26: aload 4
      // 28: invokespecial org/springframework/cglib/core/CodeGenerationException.<init> (Ljava/lang/Throwable;)V
      // 2b: athrow
      // 2c: aload 1
      // 2d: aconst_null
      // 2e: invokevirtual org/springframework/cglib/proxy/Enhancer.setClassLoader (Ljava/lang/ClassLoader;)V
      // 31: aload 1
      // 32: invokevirtual org/springframework/cglib/proxy/Enhancer.createClass ()Ljava/lang/Class;
      // 35: astore 3
      // 36: aload 3
      // 37: getstatic org/springframework/context/annotation/ConfigurationClassEnhancer.CALLBACKS [Lorg/springframework/cglib/proxy/Callback;
      // 3a: invokestatic org/springframework/cglib/proxy/Enhancer.registerStaticCallbacks (Ljava/lang/Class;[Lorg/springframework/cglib/proxy/Callback;)V
      // 3d: aload 3
      // 3e: areturn
      // try (0 -> 3): 4 java/lang/Throwable
   }

   private static class BeanFactoryAwareGeneratorStrategy extends ClassLoaderAwareGeneratorStrategy {
      public BeanFactoryAwareGeneratorStrategy(@Nullable ClassLoader classLoader) {
         super(classLoader);
      }

      protected ClassGenerator transform(ClassGenerator cg) throws Exception {
         ClassEmitterTransformer transformer = new ClassEmitterTransformer() {
            public void end_class() {
               this.declare_field(1, "$$beanFactory", Type.getType(BeanFactory.class), null);
               super.end_class();
            }
         };
         return new TransformingClassGenerator(cg, transformer);
      }
   }

   private static class BeanFactoryAwareMethodInterceptor implements MethodInterceptor, ConfigurationClassEnhancer.ConditionalCallback {
      public @Nullable Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {
         Field field = ReflectionUtils.findField(obj.getClass(), "$$beanFactory");
         Assert.state(field != null, "Unable to find generated BeanFactory field");
         field.set(obj, args[0]);
         return BeanFactoryAware.class.isAssignableFrom(ClassUtils.getUserClass(obj.getClass().getSuperclass())) ? proxy.invokeSuper(obj, args) : null;
      }

      @Override
      public boolean isMatch(Method candidateMethod) {
         return isSetBeanFactory(candidateMethod);
      }

      public static boolean isSetBeanFactory(Method candidateMethod) {
         return candidateMethod.getName().equals("setBeanFactory")
            && candidateMethod.getParameterCount() == 1
            && BeanFactory.class == candidateMethod.getParameterTypes()[0]
            && BeanFactoryAware.class.isAssignableFrom(candidateMethod.getDeclaringClass());
      }
   }

   private static class BeanMethodInterceptor implements MethodInterceptor, ConfigurationClassEnhancer.ConditionalCallback {
      public @Nullable Object intercept(Object enhancedConfigInstance, Method beanMethod, Object[] beanMethodArgs, MethodProxy cglibMethodProxy) throws Throwable {
         ConfigurableBeanFactory beanFactory = this.getBeanFactory(enhancedConfigInstance);
         String beanName = BeanAnnotationHelper.determineBeanNameFor(beanMethod, beanFactory);
         if (BeanAnnotationHelper.isScopedProxy(beanMethod)) {
            String scopedBeanName = ScopedProxyCreator.getTargetBeanName(beanName);
            if (beanFactory.isCurrentlyInCreation(scopedBeanName)) {
               beanName = scopedBeanName;
            }
         }

         String factoryBeanName = "&" + beanName;
         if (this.factoryContainsBean(beanFactory, factoryBeanName) && this.factoryContainsBean(beanFactory, beanName)) {
            Object factoryBean = beanFactory.getBean(factoryBeanName);
            if (!(factoryBean instanceof ScopedProxyFactoryBean)) {
               return this.enhanceFactoryBean(factoryBean, beanMethod.getReturnType(), beanFactory, beanName);
            }
         }

         if (this.isCurrentlyInvokedFactoryMethod(beanMethod)) {
            if (ConfigurationClassEnhancer.logger.isInfoEnabled() && BeanFactoryPostProcessor.class.isAssignableFrom(beanMethod.getReturnType())) {
               ConfigurationClassEnhancer.logger
                  .info(
                     "@Bean method %s.%s is non-static and returns an object assignable to Spring's BeanFactoryPostProcessor interface. This will result in a failure to process annotations such as @Autowired, @Resource, and @PostConstruct within the method's declaring @Configuration class. Add the 'static' modifier to this method to avoid these container lifecycle issues; see @Bean javadoc for complete details."
                        .formatted(beanMethod.getDeclaringClass().getSimpleName(), beanMethod.getName())
                  );
            }

            return cglibMethodProxy.invokeSuper(enhancedConfigInstance, beanMethodArgs);
         } else {
            return this.resolveBeanReference(beanMethod, beanMethodArgs, beanFactory, beanName);
         }
      }

      private @Nullable Object resolveBeanReference(Method beanMethod, Object[] beanMethodArgs, ConfigurableBeanFactory beanFactory, String beanName) {
         boolean alreadyInCreation = beanFactory.isCurrentlyInCreation(beanName);

         try {
            if (alreadyInCreation) {
               beanFactory.setCurrentlyInCreation(beanName, false);
            }

            boolean useArgs = !ObjectUtils.isEmpty(beanMethodArgs);
            if (useArgs && beanFactory.isSingleton(beanName)) {
               for (Object arg : beanMethodArgs) {
                  if (arg == null) {
                     useArgs = false;
                     break;
                  }
               }
            }

            Object beanInstance = useArgs ? beanFactory.getBean(beanName, beanMethodArgs) : beanFactory.getBean(beanName);
            if (!ClassUtils.isAssignableValue(beanMethod.getReturnType(), beanInstance)) {
               if (!beanInstance.equals(null)) {
                  String msg = String.format(
                     "@Bean method %s.%s called as bean reference for type [%s] but overridden by non-compatible bean instance of type [%s].",
                     beanMethod.getDeclaringClass().getSimpleName(),
                     beanMethod.getName(),
                     beanMethod.getReturnType().getName(),
                     beanInstance.getClass().getName()
                  );

                  try {
                     BeanDefinition beanDefinition = beanFactory.getMergedBeanDefinition(beanName);
                     msg = msg + " Overriding bean of same name declared in: " + beanDefinition.getResourceDescription();
                  } catch (NoSuchBeanDefinitionException var14) {
                  }

                  throw new IllegalStateException(msg);
               }

               if (ConfigurationClassEnhancer.logger.isDebugEnabled()) {
                  ConfigurationClassEnhancer.logger
                     .debug(
                        String.format(
                           "@Bean method %s.%s called as bean reference for type [%s] returned null bean; resolving to null value.",
                           beanMethod.getDeclaringClass().getSimpleName(),
                           beanMethod.getName(),
                           beanMethod.getReturnType().getName()
                        )
                     );
               }

               beanInstance = null;
            }

            Method currentlyInvoked = SimpleInstantiationStrategy.getCurrentlyInvokedFactoryMethod();
            if (currentlyInvoked != null) {
               String outerBeanName = BeanAnnotationHelper.determineBeanNameFor(currentlyInvoked, beanFactory);
               beanFactory.registerDependentBean(beanName, outerBeanName);
            }

            return beanInstance;
         } finally {
            if (alreadyInCreation) {
               beanFactory.setCurrentlyInCreation(beanName, true);
            }
         }
      }

      @Override
      public boolean isMatch(Method candidateMethod) {
         return candidateMethod.getDeclaringClass() != Object.class
            && !ConfigurationClassEnhancer.BeanFactoryAwareMethodInterceptor.isSetBeanFactory(candidateMethod)
            && BeanAnnotationHelper.isBeanAnnotated(candidateMethod);
      }

      private ConfigurableBeanFactory getBeanFactory(Object enhancedConfigInstance) {
         Field field = ReflectionUtils.findField(enhancedConfigInstance.getClass(), "$$beanFactory");
         Assert.state(field != null, "Unable to find generated bean factory field");
         Object beanFactory = ReflectionUtils.getField(field, enhancedConfigInstance);
         Assert.state(beanFactory != null, "BeanFactory has not been injected into @Configuration class");
         Assert.state(beanFactory instanceof ConfigurableBeanFactory, "Injected BeanFactory is not a ConfigurableBeanFactory");
         return (ConfigurableBeanFactory)beanFactory;
      }

      private boolean factoryContainsBean(ConfigurableBeanFactory beanFactory, String beanName) {
         return beanFactory.containsBean(beanName) && !beanFactory.isCurrentlyInCreation(beanName);
      }

      private boolean isCurrentlyInvokedFactoryMethod(Method method) {
         Method currentlyInvoked = SimpleInstantiationStrategy.getCurrentlyInvokedFactoryMethod();
         return currentlyInvoked != null
            && method.getName().equals(currentlyInvoked.getName())
            && Arrays.equals(method.getParameterTypes(), currentlyInvoked.getParameterTypes());
      }

      private Object enhanceFactoryBean(Object factoryBean, Class<?> exposedType, ConfigurableBeanFactory beanFactory, String beanName) {
         try {
            Class<?> clazz = factoryBean.getClass();
            boolean finalClass = Modifier.isFinal(clazz.getModifiers());
            boolean finalMethod = Modifier.isFinal(clazz.getMethod("getObject").getModifiers());
            if (finalClass || finalMethod) {
               if (exposedType.isInterface()) {
                  if (ConfigurationClassEnhancer.logger.isTraceEnabled()) {
                     ConfigurationClassEnhancer.logger
                        .trace(
                           "Creating interface proxy for FactoryBean '"
                              + beanName
                              + "' of type ["
                              + clazz.getName()
                              + "] for use within another @Bean method because its "
                              + (finalClass ? "implementation class" : "getObject() method")
                              + " is final: Otherwise a getObject() call would not be routed to the factory."
                        );
                  }

                  return this.createInterfaceProxyForFactoryBean(factoryBean, exposedType, beanFactory, beanName);
               }

               if (ConfigurationClassEnhancer.logger.isDebugEnabled()) {
                  ConfigurationClassEnhancer.logger
                     .debug(
                        "Unable to proxy FactoryBean '"
                           + beanName
                           + "' of type ["
                           + clazz.getName()
                           + "] for use within another @Bean method because its "
                           + (finalClass ? "implementation class" : "getObject() method")
                           + " is final: A getObject() call will NOT be routed to the factory. Consider declaring the return type as a FactoryBean interface."
                     );
               }

               return factoryBean;
            }
         } catch (NoSuchMethodException var8) {
         }

         return this.createCglibProxyForFactoryBean(factoryBean, beanFactory, beanName);
      }

      private Object createInterfaceProxyForFactoryBean(Object factoryBean, Class<?> interfaceType, ConfigurableBeanFactory beanFactory, String beanName) {
         return Proxy.newProxyInstance(
            factoryBean.getClass().getClassLoader(),
            new Class[]{interfaceType},
            (proxy, method, args) -> method.getName().equals("getObject") && args == null
               ? beanFactory.getBean(beanName)
               : ReflectionUtils.invokeMethod(method, factoryBean, args)
         );
      }

      private Object createCglibProxyForFactoryBean(Object factoryBean, ConfigurableBeanFactory beanFactory, String beanName) {
         Enhancer enhancer = new Enhancer();
         enhancer.setSuperclass(factoryBean.getClass());
         enhancer.setNamingPolicy(SpringNamingPolicy.INSTANCE);
         enhancer.setAttemptLoad(AotDetector.useGeneratedArtifacts());
         enhancer.setCallbackType(MethodInterceptor.class);
         Class<?> fbClass = enhancer.createClass();
         Object fbProxy = null;
         if (ConfigurationClassEnhancer.objenesis.isWorthTrying()) {
            try {
               fbProxy = ConfigurationClassEnhancer.objenesis.newInstance(fbClass, enhancer.getUseCache());
            } catch (ObjenesisException ex) {
               ConfigurationClassEnhancer.logger.debug("Unable to instantiate enhanced FactoryBean using Objenesis, falling back to regular construction", ex);
            }
         }

         if (fbProxy == null) {
            try {
               fbProxy = ReflectionUtils.accessibleConstructor(fbClass, new Class[0]).newInstance();
            } catch (Throwable ex) {
               throw new IllegalStateException(
                  "Unable to instantiate enhanced FactoryBean using Objenesis, and regular FactoryBean instantiation via default constructor fails as well", ex
               );
            }
         }

         ((Factory)fbProxy)
            .setCallback(
               0,
               (MethodInterceptor)(obj, method, args, proxy) -> method.getName().equals("getObject") && args.length == 0
                  ? beanFactory.getBean(beanName)
                  : method.invoke(factoryBean, args)
            );
         return fbProxy;
      }
   }

   private interface ConditionalCallback extends Callback {
      boolean isMatch(Method candidateMethod);
   }

   private static class ConditionalCallbackFilter implements CallbackFilter {
      private final Callback[] callbacks;
      private final Class<?>[] callbackTypes;

      public ConditionalCallbackFilter(Callback[] callbacks) {
         this.callbacks = callbacks;
         this.callbackTypes = new Class[callbacks.length];

         for (int i = 0; i < callbacks.length; i++) {
            this.callbackTypes[i] = callbacks[i].getClass();
         }
      }

      public int accept(Method method) {
         for (int i = 0; i < this.callbacks.length; i++) {
            if (!(this.callbacks[i] instanceof ConfigurationClassEnhancer.ConditionalCallback conditional) || conditional.isMatch(method)) {
               return i;
            }
         }

         throw new IllegalStateException("No callback available for method " + method.getName());
      }

      public Class<?>[] getCallbackTypes() {
         return this.callbackTypes;
      }
   }

   public interface EnhancedConfiguration extends BeanFactoryAware {
   }
}
