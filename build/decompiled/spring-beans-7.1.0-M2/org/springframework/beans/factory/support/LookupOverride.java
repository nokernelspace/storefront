package org.springframework.beans.factory.support;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ObjectUtils;

public class LookupOverride extends MethodOverride {
   private final @Nullable String beanName;
   private @Nullable Method method;

   public LookupOverride(String methodName, @Nullable String beanName) {
      super(methodName);
      this.beanName = beanName;
   }

   public LookupOverride(Method method, @Nullable String beanName) {
      super(method.getName());
      this.method = method;
      this.beanName = beanName;
   }

   public @Nullable String getBeanName() {
      return this.beanName;
   }

   @Override
   public boolean matches(Method method) {
      return this.method != null
         ? method.equals(this.method)
         : method.getName().equals(this.getMethodName())
            && (!this.isOverloaded() || Modifier.isAbstract(method.getModifiers()) || method.getParameterCount() == 0);
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return other instanceof LookupOverride that
         && super.equals(other)
         && ObjectUtils.nullSafeEquals(this.method, that.method)
         && ObjectUtils.nullSafeEquals(this.beanName, that.beanName);
   }

   @Override
   public int hashCode() {
      return super.hashCode() * 29 + ObjectUtils.nullSafeHashCode(this.beanName);
   }

   @Override
   public String toString() {
      return "LookupOverride for method '" + this.getMethodName() + "'";
   }
}
