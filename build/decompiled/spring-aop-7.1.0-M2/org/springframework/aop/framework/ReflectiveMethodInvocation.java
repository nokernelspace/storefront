package org.springframework.aop.framework;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.BridgeMethodResolver;

public class ReflectiveMethodInvocation implements ProxyMethodInvocation, Cloneable {
   protected final Object proxy;
   protected final @Nullable Object target;
   protected final Method method;
   protected @Nullable Object[] arguments;
   private final @Nullable Class<?> targetClass;
   private @Nullable Map<String, Object> userAttributes;
   protected final List<?> interceptorsAndDynamicMethodMatchers;
   private int currentInterceptorIndex = -1;

   protected ReflectiveMethodInvocation(
      Object proxy,
      @Nullable Object target,
      Method method,
      @Nullable Object[] arguments,
      @Nullable Class<?> targetClass,
      List<Object> interceptorsAndDynamicMethodMatchers
   ) {
      this.proxy = proxy;
      this.target = target;
      this.targetClass = targetClass;
      this.method = BridgeMethodResolver.findBridgedMethod(method);
      this.arguments = AopProxyUtils.adaptArgumentsIfNecessary(method, arguments);
      this.interceptorsAndDynamicMethodMatchers = interceptorsAndDynamicMethodMatchers;
   }

   @Override
   public final Object getProxy() {
      return this.proxy;
   }

   @Override
   public final @Nullable Object getThis() {
      return this.target;
   }

   @Override
   public final AccessibleObject getStaticPart() {
      return this.method;
   }

   @Override
   public final Method getMethod() {
      return this.method;
   }

   @Override
   public final @Nullable Object[] getArguments() {
      return this.arguments;
   }

   @Override
   public void setArguments(@Nullable Object... arguments) {
      this.arguments = arguments;
   }

   @Override
   public @Nullable Object proceed() throws Throwable {
      if (this.currentInterceptorIndex == this.interceptorsAndDynamicMethodMatchers.size() - 1) {
         return this.invokeJoinpoint();
      } else {
         Object interceptorOrInterceptionAdvice = this.interceptorsAndDynamicMethodMatchers.get(++this.currentInterceptorIndex);
         if (interceptorOrInterceptionAdvice instanceof InterceptorAndDynamicMethodMatcher dm) {
            Class<?> targetClass = this.targetClass != null ? this.targetClass : this.method.getDeclaringClass();
            return dm.matcher().matches(this.method, targetClass, this.arguments) ? dm.interceptor().invoke(this) : this.proceed();
         } else {
            return ((MethodInterceptor)interceptorOrInterceptionAdvice).invoke(this);
         }
      }
   }

   protected @Nullable Object invokeJoinpoint() throws Throwable {
      return AopUtils.invokeJoinpointUsingReflection(this.target, this.method, this.arguments);
   }

   @Override
   public MethodInvocation invocableClone() {
      Object[] cloneArguments = this.arguments;
      if (this.arguments.length > 0) {
         cloneArguments = (Object[])this.arguments.clone();
      }

      return this.invocableClone(cloneArguments);
   }

   @Override
   public MethodInvocation invocableClone(@Nullable Object... arguments) {
      if (this.userAttributes == null) {
         this.userAttributes = new HashMap<>();
      }

      try {
         ReflectiveMethodInvocation clone = (ReflectiveMethodInvocation)this.clone();
         clone.arguments = arguments;
         return clone;
      } catch (CloneNotSupportedException ex) {
         throw new IllegalStateException("Should be able to clone object of type [" + this.getClass() + "]: " + ex);
      }
   }

   @Override
   public void setUserAttribute(String key, @Nullable Object value) {
      if (value != null) {
         if (this.userAttributes == null) {
            this.userAttributes = new HashMap<>();
         }

         this.userAttributes.put(key, value);
      } else if (this.userAttributes != null) {
         this.userAttributes.remove(key);
      }
   }

   @Override
   public @Nullable Object getUserAttribute(String key) {
      return this.userAttributes != null ? this.userAttributes.get(key) : null;
   }

   public Map<String, Object> getUserAttributes() {
      if (this.userAttributes == null) {
         this.userAttributes = new HashMap<>();
      }

      return this.userAttributes;
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder("ReflectiveMethodInvocation: ");
      sb.append(this.method).append("; ");
      if (this.target == null) {
         sb.append("target is null");
      } else {
         sb.append("target is of class [").append(this.target.getClass().getName()).append(']');
      }

      return sb.toString();
   }
}
