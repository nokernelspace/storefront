package org.springframework.jndi;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import javax.naming.Context;
import javax.naming.NamingException;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.SimpleTypeConverter;
import org.springframework.beans.TypeConverter;
import org.springframework.beans.TypeMismatchException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

public class JndiObjectFactoryBean extends JndiObjectLocator implements FactoryBean<Object>, BeanFactoryAware, BeanClassLoaderAware {
   private Class<?> @Nullable [] proxyInterfaces;
   private boolean lookupOnStartup = true;
   private boolean cache = true;
   private boolean exposeAccessContext = false;
   private @Nullable Object defaultObject;
   private @Nullable ConfigurableBeanFactory beanFactory;
   private @Nullable ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();
   private @Nullable Object jndiObject;

   public void setProxyInterface(Class<?> proxyInterface) {
      this.proxyInterfaces = new Class[]{proxyInterface};
   }

   public void setProxyInterfaces(Class<?>... proxyInterfaces) {
      this.proxyInterfaces = proxyInterfaces;
   }

   public void setLookupOnStartup(boolean lookupOnStartup) {
      this.lookupOnStartup = lookupOnStartup;
   }

   public void setCache(boolean cache) {
      this.cache = cache;
   }

   public void setExposeAccessContext(boolean exposeAccessContext) {
      this.exposeAccessContext = exposeAccessContext;
   }

   public void setDefaultObject(Object defaultObject) {
      this.defaultObject = defaultObject;
   }

   public void setBeanFactory(BeanFactory beanFactory) {
      if (beanFactory instanceof ConfigurableBeanFactory cbf) {
         this.beanFactory = cbf;
      }
   }

   public void setBeanClassLoader(ClassLoader classLoader) {
      this.beanClassLoader = classLoader;
   }

   @Override
   public void afterPropertiesSet() throws IllegalArgumentException, NamingException {
      super.afterPropertiesSet();
      if (this.proxyInterfaces == null && this.lookupOnStartup && this.cache && !this.exposeAccessContext) {
         if (this.defaultObject != null && this.getExpectedType() != null && !this.getExpectedType().isInstance(this.defaultObject)) {
            TypeConverter converter = (TypeConverter)(this.beanFactory != null ? this.beanFactory.getTypeConverter() : new SimpleTypeConverter());

            try {
               this.defaultObject = converter.convertIfNecessary(this.defaultObject, this.getExpectedType());
            } catch (TypeMismatchException ex) {
               throw new IllegalArgumentException(
                  "Default object ["
                     + this.defaultObject
                     + "] of type ["
                     + this.defaultObject.getClass().getName()
                     + "] is not of expected type ["
                     + this.getExpectedType().getName()
                     + "] and cannot be converted either",
                  ex
               );
            }
         }

         this.jndiObject = this.lookupWithFallback();
      } else {
         if (this.defaultObject != null) {
            throw new IllegalArgumentException("'defaultObject' is not supported in combination with 'proxyInterface'");
         }

         this.jndiObject = JndiObjectFactoryBean.JndiObjectProxyFactory.createJndiObjectProxy(this);
      }
   }

   protected Object lookupWithFallback() throws NamingException {
      ClassLoader originalClassLoader = ClassUtils.overrideThreadContextClassLoader(this.beanClassLoader);

      try {
         return this.lookup();
      } catch (TypeMismatchNamingException ex) {
         throw ex;
      } catch (NamingException ex) {
         if (this.defaultObject != null) {
            if (this.logger.isTraceEnabled()) {
               this.logger.trace("JNDI lookup failed - returning specified default object instead", ex);
            } else if (this.logger.isDebugEnabled()) {
               this.logger.debug("JNDI lookup failed - returning specified default object instead: " + ex);
            }

            return this.defaultObject;
         } else {
            throw ex;
         }
      } finally {
         if (originalClassLoader != null) {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
         }
      }
   }

   public @Nullable Object getObject() {
      return this.jndiObject;
   }

   public @Nullable Class<?> getObjectType() {
      if (this.proxyInterfaces != null) {
         if (this.proxyInterfaces.length == 1) {
            return this.proxyInterfaces[0];
         }

         if (this.proxyInterfaces.length > 1) {
            return this.createCompositeInterface(this.proxyInterfaces);
         }
      }

      return this.jndiObject != null ? this.jndiObject.getClass() : this.getExpectedType();
   }

   public boolean isSingleton() {
      return true;
   }

   protected Class<?> createCompositeInterface(Class<?>[] interfaces) {
      return ClassUtils.createCompositeInterface(interfaces, this.beanClassLoader);
   }

   private static class JndiContextExposingInterceptor implements MethodInterceptor {
      private final JndiTemplate jndiTemplate;

      public JndiContextExposingInterceptor(JndiTemplate jndiTemplate) {
         this.jndiTemplate = jndiTemplate;
      }

      public @Nullable Object invoke(MethodInvocation invocation) throws Throwable {
         Context ctx = this.isEligible(invocation.getMethod()) ? this.jndiTemplate.getContext() : null;

         try {
            return invocation.proceed();
         } finally {
            this.jndiTemplate.releaseContext(ctx);
         }
      }

      protected boolean isEligible(Method method) {
         return Object.class != method.getDeclaringClass();
      }
   }

   private static class JndiObjectProxyFactory {
      private static Object createJndiObjectProxy(JndiObjectFactoryBean jof) throws NamingException {
         JndiObjectTargetSource targetSource = new JndiObjectTargetSource();
         targetSource.setJndiTemplate(jof.getJndiTemplate());
         String jndiName = jof.getJndiName();
         Assert.state(jndiName != null, "No JNDI name specified");
         targetSource.setJndiName(jndiName);
         targetSource.setExpectedType(jof.getExpectedType());
         targetSource.setResourceRef(jof.isResourceRef());
         targetSource.setLookupOnStartup(jof.lookupOnStartup);
         targetSource.setCache(jof.cache);
         targetSource.afterPropertiesSet();
         ProxyFactory proxyFactory = new ProxyFactory();
         if (jof.proxyInterfaces != null) {
            proxyFactory.setInterfaces(jof.proxyInterfaces);
         } else {
            Class<?> targetClass = targetSource.getTargetClass();
            if (targetClass == null) {
               throw new IllegalStateException("Cannot deactivate 'lookupOnStartup' without specifying a 'proxyInterface' or 'expectedType'");
            }

            Class<?>[] ifcs = ClassUtils.getAllInterfacesForClass(targetClass, jof.beanClassLoader);

            for (Class<?> ifc : ifcs) {
               if (Modifier.isPublic(ifc.getModifiers())) {
                  proxyFactory.addInterface(ifc);
               }
            }
         }

         if (jof.exposeAccessContext) {
            proxyFactory.addAdvice(new JndiObjectFactoryBean.JndiContextExposingInterceptor(jof.getJndiTemplate()));
         }

         proxyFactory.setTargetSource(targetSource);
         return proxyFactory.getProxy(jof.beanClassLoader);
      }
   }
}
