package org.springframework.beans.factory.config;

import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public class BeanExpressionContext {
   private final ConfigurableBeanFactory beanFactory;
   private final @Nullable Scope scope;

   public BeanExpressionContext(ConfigurableBeanFactory beanFactory, @Nullable Scope scope) {
      Assert.notNull(beanFactory, "BeanFactory must not be null");
      this.beanFactory = beanFactory;
      this.scope = scope;
   }

   public final ConfigurableBeanFactory getBeanFactory() {
      return this.beanFactory;
   }

   public final @Nullable Scope getScope() {
      return this.scope;
   }

   public boolean containsObject(String key) {
      return this.beanFactory.containsBean(key) || this.scope != null && this.scope.resolveContextualObject(key) != null;
   }

   public @Nullable Object getObject(String key) {
      if (this.beanFactory.containsBean(key)) {
         return this.beanFactory.getBean(key);
      } else {
         return this.scope != null ? this.scope.resolveContextualObject(key) : null;
      }
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof BeanExpressionContext that && this.beanFactory == that.beanFactory && this.scope == that.scope;
   }

   @Override
   public int hashCode() {
      return this.beanFactory.hashCode();
   }
}
