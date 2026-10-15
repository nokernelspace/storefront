package org.springframework.context.annotation;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.TargetSource;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.config.DependencyDescriptor;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.core.MethodParameter;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

public abstract class ResourceElementResolver {
   private final String name;
   private final boolean defaultName;

   ResourceElementResolver(String name, boolean defaultName) {
      this.name = name;
      this.defaultName = defaultName;
   }

   public static ResourceElementResolver forField(String fieldName) {
      return new ResourceElementResolver.ResourceFieldResolver(fieldName, true, fieldName);
   }

   public static ResourceElementResolver forField(String fieldName, String resourceName) {
      return new ResourceElementResolver.ResourceFieldResolver(resourceName, false, fieldName);
   }

   public static ResourceElementResolver forMethod(String methodName, Class<?> parameterType) {
      return new ResourceElementResolver.ResourceMethodResolver(defaultResourceNameForMethod(methodName), true, methodName, parameterType);
   }

   public static ResourceElementResolver forMethod(String methodName, Class<?> parameterType, String resourceName) {
      return new ResourceElementResolver.ResourceMethodResolver(resourceName, false, methodName, parameterType);
   }

   private static String defaultResourceNameForMethod(String methodName) {
      return methodName.startsWith("set") && methodName.length() > 3 ? StringUtils.uncapitalizeAsProperty(methodName.substring(3)) : methodName;
   }

   public <T> @Nullable T resolve(RegisteredBean registeredBean) {
      Assert.notNull(registeredBean, "'registeredBean' must not be null");
      return (T)(this.isLazyLookup(registeredBean) ? this.buildLazyResourceProxy(registeredBean) : this.resolveValue(registeredBean));
   }

   public abstract void resolveAndSet(RegisteredBean registeredBean, Object instance);

   abstract DependencyDescriptor createDependencyDescriptor(RegisteredBean registeredBean);

   abstract Class<?> getLookupType(RegisteredBean registeredBean);

   abstract AnnotatedElement getAnnotatedElement(RegisteredBean registeredBean);

   boolean isLazyLookup(RegisteredBean registeredBean) {
      AnnotatedElement ae = this.getAnnotatedElement(registeredBean);
      Lazy lazy = ae.getAnnotation(Lazy.class);
      return lazy != null && lazy.value();
   }

   private Object buildLazyResourceProxy(RegisteredBean registeredBean) {
      final Class<?> lookupType = this.getLookupType(registeredBean);
      TargetSource ts = new TargetSource() {
         public Class<?> getTargetClass() {
            return lookupType;
         }

         public Object getTarget() {
            return ResourceElementResolver.this.resolveValue(registeredBean);
         }
      };
      ProxyFactory pf = new ProxyFactory();
      pf.setTargetSource(ts);
      if (lookupType.isInterface()) {
         pf.addInterface(lookupType);
      }

      return pf.getProxy(registeredBean.getBeanFactory().getBeanClassLoader());
   }

   private Object resolveValue(RegisteredBean registeredBean) {
      ConfigurableListableBeanFactory factory = registeredBean.getBeanFactory();
      DependencyDescriptor descriptor = this.createDependencyDescriptor(registeredBean);
      Object resource;
      Set<String> autowiredBeanNames;
      if (this.defaultName && !factory.containsBean(this.name)) {
         autowiredBeanNames = new LinkedHashSet<>();
         resource = factory.resolveDependency(descriptor, registeredBean.getBeanName(), autowiredBeanNames, null);
         if (resource == null) {
            throw new NoSuchBeanDefinitionException(descriptor.getDependencyType(), "No resolvable resource object");
         }
      } else {
         resource = factory.resolveBeanByName(this.name, descriptor);
         autowiredBeanNames = Collections.singleton(this.name);
      }

      for (String autowiredBeanName : autowiredBeanNames) {
         if (factory.containsBean(autowiredBeanName)) {
            factory.registerDependentBean(autowiredBeanName, registeredBean.getBeanName());
         }
      }

      return resource;
   }

   static class LookupDependencyDescriptor extends DependencyDescriptor {
      private final Class<?> lookupType;
      private final boolean lazyLookup;

      public LookupDependencyDescriptor(Field field, Class<?> lookupType, boolean lazyLookup) {
         super(field, true);
         this.lookupType = lookupType;
         this.lazyLookup = lazyLookup;
      }

      public LookupDependencyDescriptor(Method method, Class<?> lookupType, boolean lazyLookup) {
         super(new MethodParameter(method, 0), true);
         this.lookupType = lookupType;
         this.lazyLookup = lazyLookup;
      }

      public Class<?> getDependencyType() {
         return this.lookupType;
      }

      public boolean supportsLazyResolution() {
         return !this.lazyLookup;
      }
   }

   private static final class ResourceFieldResolver extends ResourceElementResolver {
      private final String fieldName;

      public ResourceFieldResolver(String name, boolean defaultName, String fieldName) {
         super(name, defaultName);
         this.fieldName = fieldName;
      }

      @Override
      public void resolveAndSet(RegisteredBean registeredBean, Object instance) {
         Assert.notNull(registeredBean, "'registeredBean' must not be null");
         Assert.notNull(instance, "'instance' must not be null");
         Field field = this.getField(registeredBean);
         Object resolved = this.resolve(registeredBean);
         ReflectionUtils.makeAccessible(field);
         ReflectionUtils.setField(field, instance, resolved);
      }

      @Override
      protected DependencyDescriptor createDependencyDescriptor(RegisteredBean registeredBean) {
         Field field = this.getField(registeredBean);
         return new ResourceElementResolver.LookupDependencyDescriptor(field, field.getType(), this.isLazyLookup(registeredBean));
      }

      @Override
      protected Class<?> getLookupType(RegisteredBean registeredBean) {
         return this.getField(registeredBean).getType();
      }

      @Override
      protected AnnotatedElement getAnnotatedElement(RegisteredBean registeredBean) {
         return this.getField(registeredBean);
      }

      private Field getField(RegisteredBean registeredBean) {
         Field field = ReflectionUtils.findField(registeredBean.getBeanClass(), this.fieldName);
         Assert.notNull(field, () -> "No field '" + this.fieldName + "' found on " + registeredBean.getBeanClass().getName());
         return field;
      }
   }

   private static final class ResourceMethodResolver extends ResourceElementResolver {
      private final String methodName;
      private final Class<?> lookupType;

      private ResourceMethodResolver(String name, boolean defaultName, String methodName, Class<?> lookupType) {
         super(name, defaultName);
         this.methodName = methodName;
         this.lookupType = lookupType;
      }

      @Override
      public void resolveAndSet(RegisteredBean registeredBean, Object instance) {
         Assert.notNull(registeredBean, "'registeredBean' must not be null");
         Assert.notNull(instance, "'instance' must not be null");
         Method method = this.getMethod(registeredBean);
         Object resolved = this.resolve(registeredBean);
         ReflectionUtils.makeAccessible(method);
         ReflectionUtils.invokeMethod(method, instance, new Object[]{resolved});
      }

      @Override
      protected DependencyDescriptor createDependencyDescriptor(RegisteredBean registeredBean) {
         return new ResourceElementResolver.LookupDependencyDescriptor(this.getMethod(registeredBean), this.lookupType, this.isLazyLookup(registeredBean));
      }

      @Override
      protected Class<?> getLookupType(RegisteredBean bean) {
         return this.lookupType;
      }

      @Override
      protected AnnotatedElement getAnnotatedElement(RegisteredBean registeredBean) {
         return this.getMethod(registeredBean);
      }

      private Method getMethod(RegisteredBean registeredBean) {
         Method method = ReflectionUtils.findMethod(registeredBean.getBeanClass(), this.methodName, new Class[]{this.lookupType});
         Assert.notNull(
            method,
            () -> "Method '%s' with parameter type '%s' declared on %s could not be found."
               .formatted(this.methodName, this.lookupType.getName(), registeredBean.getBeanClass().getName())
         );
         return method;
      }
   }
}
