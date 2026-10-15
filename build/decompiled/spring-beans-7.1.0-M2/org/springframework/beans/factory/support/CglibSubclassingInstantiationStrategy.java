package org.springframework.beans.factory.support;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.aot.AotDetector;
import org.springframework.beans.BeanInstantiationException;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.cglib.core.ClassLoaderAwareGeneratorStrategy;
import org.springframework.cglib.core.SpringNamingPolicy;
import org.springframework.cglib.proxy.Callback;
import org.springframework.cglib.proxy.CallbackFilter;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.cglib.proxy.Factory;
import org.springframework.cglib.proxy.MethodInterceptor;
import org.springframework.cglib.proxy.MethodProxy;
import org.springframework.cglib.proxy.NoOp;
import org.springframework.core.ResolvableType;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class CglibSubclassingInstantiationStrategy extends SimpleInstantiationStrategy {
   private static final int PASSTHROUGH = 0;
   private static final int LOOKUP_OVERRIDE = 1;
   private static final int METHOD_REPLACER = 2;

   @Override
   protected Object instantiateWithMethodInjection(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner) {
      return this.instantiateWithMethodInjection(bd, beanName, owner, null);
   }

   @Override
   protected Object instantiateWithMethodInjection(
      RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner, @Nullable Constructor<?> ctor, Object... args
   ) {
      return new CglibSubclassingInstantiationStrategy.CglibSubclassCreator(bd, owner).instantiate(ctor, args);
   }

   @Override
   public Class<?> getActualBeanClass(RootBeanDefinition bd, @Nullable String beanName, BeanFactory owner) {
      return !bd.hasMethodOverrides()
         ? super.getActualBeanClass(bd, beanName, owner)
         : new CglibSubclassingInstantiationStrategy.CglibSubclassCreator(bd, owner).createEnhancedSubclass(bd);
   }

   private static class CglibIdentitySupport {
      private final RootBeanDefinition beanDefinition;

      public CglibIdentitySupport(RootBeanDefinition beanDefinition) {
         this.beanDefinition = beanDefinition;
      }

      public RootBeanDefinition getBeanDefinition() {
         return this.beanDefinition;
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return other != null
            && this.getClass() == other.getClass()
            && this.beanDefinition.equals(((CglibSubclassingInstantiationStrategy.CglibIdentitySupport)other).beanDefinition);
      }

      @Override
      public int hashCode() {
         return this.beanDefinition.hashCode();
      }
   }

   private static class CglibSubclassCreator {
      private static final Class<?>[] CALLBACK_TYPES = new Class[]{
         NoOp.class,
         CglibSubclassingInstantiationStrategy.LookupOverrideMethodInterceptor.class,
         CglibSubclassingInstantiationStrategy.ReplaceOverrideMethodInterceptor.class
      };
      private final RootBeanDefinition beanDefinition;
      private final BeanFactory owner;

      CglibSubclassCreator(RootBeanDefinition beanDefinition, BeanFactory owner) {
         this.beanDefinition = beanDefinition;
         this.owner = owner;
      }

      public Object instantiate(@Nullable Constructor<?> ctor, Object... args) {
         Class<?> subclass = this.createEnhancedSubclass(this.beanDefinition);
         Object instance;
         if (ctor == null) {
            instance = BeanUtils.instantiateClass(subclass);
         } else {
            try {
               Constructor<?> enhancedSubclassConstructor = subclass.getConstructor(ctor.getParameterTypes());
               instance = enhancedSubclassConstructor.newInstance(args);
            } catch (Exception ex) {
               throw new BeanInstantiationException(
                  this.beanDefinition.getBeanClass(), "Failed to invoke constructor for CGLIB enhanced subclass [" + subclass.getName() + "]", ex
               );
            }
         }

         Factory factory = (Factory)instance;
         factory.setCallbacks(
            new Callback[]{
               NoOp.INSTANCE,
               new CglibSubclassingInstantiationStrategy.LookupOverrideMethodInterceptor(this.beanDefinition, this.owner),
               new CglibSubclassingInstantiationStrategy.ReplaceOverrideMethodInterceptor(this.beanDefinition, this.owner)
            }
         );
         return instance;
      }

      public Class<?> createEnhancedSubclass(RootBeanDefinition beanDefinition) {
         Enhancer enhancer = new Enhancer();
         enhancer.setSuperclass(beanDefinition.getBeanClass());
         enhancer.setNamingPolicy(SpringNamingPolicy.INSTANCE);
         enhancer.setAttemptLoad(AotDetector.useGeneratedArtifacts());
         if (this.owner instanceof ConfigurableBeanFactory cbf) {
            ClassLoader cl = cbf.getBeanClassLoader();
            enhancer.setStrategy(new ClassLoaderAwareGeneratorStrategy(cl));
         }

         enhancer.setCallbackFilter(new CglibSubclassingInstantiationStrategy.MethodOverrideCallbackFilter(beanDefinition));
         enhancer.setCallbackTypes(CALLBACK_TYPES);
         return enhancer.createClass();
      }
   }

   private static class LookupOverrideMethodInterceptor extends CglibSubclassingInstantiationStrategy.CglibIdentitySupport implements MethodInterceptor {
      private final BeanFactory owner;

      public LookupOverrideMethodInterceptor(RootBeanDefinition beanDefinition, BeanFactory owner) {
         super(beanDefinition);
         this.owner = owner;
      }

      public @Nullable Object intercept(Object obj, Method method, Object[] args, MethodProxy mp) throws Throwable {
         LookupOverride lo = (LookupOverride)this.getBeanDefinition().getMethodOverrides().getOverride(method);
         Assert.state(lo != null, "LookupOverride not found");
         Object[] argsToUse = args.length > 0 ? args : null;
         if (StringUtils.hasText(lo.getBeanName())) {
            Object bean = argsToUse != null ? this.owner.getBean(lo.getBeanName(), argsToUse) : this.owner.getBean(lo.getBeanName());
            return bean.equals(null) ? null : bean;
         } else {
            ResolvableType genericReturnType = ResolvableType.forMethodReturnType(method);
            return argsToUse != null
               ? this.owner.getBeanProvider(genericReturnType).getObject(argsToUse)
               : this.owner.getBeanProvider(genericReturnType).getObject();
         }
      }
   }

   private static class MethodOverrideCallbackFilter extends CglibSubclassingInstantiationStrategy.CglibIdentitySupport implements CallbackFilter {
      private static final Log logger = LogFactory.getLog(CglibSubclassingInstantiationStrategy.MethodOverrideCallbackFilter.class);

      public MethodOverrideCallbackFilter(RootBeanDefinition beanDefinition) {
         super(beanDefinition);
      }

      public int accept(Method method) {
         MethodOverride methodOverride = this.getBeanDefinition().getMethodOverrides().getOverride(method);
         if (logger.isTraceEnabled()) {
            logger.trace("MethodOverride for " + method + ": " + methodOverride);
         }

         if (methodOverride == null) {
            return 0;
         } else if (methodOverride instanceof LookupOverride) {
            return 1;
         } else if (methodOverride instanceof ReplaceOverride) {
            return 2;
         } else {
            throw new UnsupportedOperationException("Unexpected MethodOverride subclass: " + methodOverride.getClass().getName());
         }
      }
   }

   private static class ReplaceOverrideMethodInterceptor extends CglibSubclassingInstantiationStrategy.CglibIdentitySupport implements MethodInterceptor {
      private final BeanFactory owner;

      public ReplaceOverrideMethodInterceptor(RootBeanDefinition beanDefinition, BeanFactory owner) {
         super(beanDefinition);
         this.owner = owner;
      }

      public @Nullable Object intercept(Object obj, Method method, Object[] args, MethodProxy mp) throws Throwable {
         ReplaceOverride ro = (ReplaceOverride)this.getBeanDefinition().getMethodOverrides().getOverride(method);
         Assert.state(ro != null, "ReplaceOverride not found");
         MethodReplacer mr = this.owner.getBean(ro.getMethodReplacerBeanName(), MethodReplacer.class);
         return this.processReturnType(method, mr.reimplement(obj, method, args));
      }

      private <T> @Nullable T processReturnType(Method method, @Nullable T returnValue) {
         Class<?> returnType = method.getReturnType();
         if (returnValue == null && returnType != void.class && returnType.isPrimitive()) {
            throw new IllegalStateException("Null return value from MethodReplacer does not match primitive return type for: " + method);
         } else {
            return returnValue;
         }
      }
   }
}
