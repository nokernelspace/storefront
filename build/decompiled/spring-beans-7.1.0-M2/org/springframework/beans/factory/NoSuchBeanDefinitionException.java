package org.springframework.beans.factory;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.core.ResolvableType;

public class NoSuchBeanDefinitionException extends BeansException {
   private final @Nullable String beanName;
   private final @Nullable ResolvableType resolvableType;

   public NoSuchBeanDefinitionException(String name) {
      super("No bean named '" + name + "' available");
      this.beanName = name;
      this.resolvableType = null;
   }

   public NoSuchBeanDefinitionException(String name, String message) {
      super("No bean named '" + name + "' available: " + message);
      this.beanName = name;
      this.resolvableType = null;
   }

   public NoSuchBeanDefinitionException(Class<?> type) {
      this(ResolvableType.forClass(type));
   }

   public NoSuchBeanDefinitionException(Class<?> type, String message) {
      this(ResolvableType.forClass(type), message);
   }

   public NoSuchBeanDefinitionException(ResolvableType type) {
      super("No qualifying bean of type '" + type + "' available");
      this.beanName = null;
      this.resolvableType = type;
   }

   public NoSuchBeanDefinitionException(ResolvableType type, String message) {
      super("No qualifying bean of type '" + type + "' available: " + message);
      this.beanName = null;
      this.resolvableType = type;
   }

   public @Nullable String getBeanName() {
      return this.beanName;
   }

   public @Nullable Class<?> getBeanType() {
      return this.resolvableType != null ? this.resolvableType.resolve() : null;
   }

   public @Nullable ResolvableType getResolvableType() {
      return this.resolvableType;
   }

   public int getNumberOfBeansFound() {
      return 0;
   }
}
