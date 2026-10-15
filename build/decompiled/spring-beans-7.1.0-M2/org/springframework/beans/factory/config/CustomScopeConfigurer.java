package org.springframework.beans.factory.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.core.Ordered;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

public class CustomScopeConfigurer implements BeanFactoryPostProcessor, BeanClassLoaderAware, Ordered {
   private @Nullable Map<String, Object> scopes;
   private int order = Integer.MAX_VALUE;
   private @Nullable ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

   public void setScopes(Map<String, Object> scopes) {
      this.scopes = scopes;
   }

   public void addScope(String scopeName, Scope scope) {
      if (this.scopes == null) {
         this.scopes = new LinkedHashMap<>(1);
      }

      this.scopes.put(scopeName, scope);
   }

   public void setOrder(int order) {
      this.order = order;
   }

   public int getOrder() {
      return this.order;
   }

   @Override
   public void setBeanClassLoader(@Nullable ClassLoader beanClassLoader) {
      this.beanClassLoader = beanClassLoader;
   }

   @Override
   public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
      if (this.scopes != null) {
         this.scopes
            .forEach(
               (scopeKey, value) -> {
                  if (value instanceof Scope scope) {
                     beanFactory.registerScope(scopeKey, scope);
                  } else if (value instanceof Class<?> scopeClass) {
                     Assert.isAssignable(Scope.class, scopeClass, "Invalid scope class");
                     beanFactory.registerScope(scopeKey, BeanUtils.instantiateClass((Class<Scope>)scopeClass));
                  } else {
                     if (!(value instanceof String scopeClassName)) {
                        throw new IllegalArgumentException(
                           "Mapped value ["
                              + value
                              + "] for scope key ["
                              + scopeKey
                              + "] is not an instance of required type ["
                              + Scope.class.getName()
                              + "] or a corresponding Class or String value indicating a Scope implementation"
                        );
                     }

                     Class<?> scopeClass = ClassUtils.resolveClassName(scopeClassName, this.beanClassLoader);
                     Assert.isAssignable(Scope.class, scopeClass, "Invalid scope class");
                     beanFactory.registerScope(scopeKey, BeanUtils.instantiateClass((Class<Scope>)scopeClass));
                  }
               }
            );
      }
   }
}
