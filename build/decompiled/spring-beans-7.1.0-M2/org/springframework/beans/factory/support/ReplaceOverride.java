package org.springframework.beans.factory.support;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public class ReplaceOverride extends MethodOverride {
   private final String methodReplacerBeanName;
   private final List<String> typeIdentifiers = new ArrayList<>();

   public ReplaceOverride(String methodName, String methodReplacerBeanName) {
      super(methodName);
      Assert.notNull(methodReplacerBeanName, "Method replacer bean name must not be null");
      this.methodReplacerBeanName = methodReplacerBeanName;
   }

   public ReplaceOverride(String methodName, String methodReplacerBeanName, List<String> typeIdentifiers) {
      super(methodName);
      Assert.notNull(methodReplacerBeanName, "Method replacer bean name must not be null");
      this.methodReplacerBeanName = methodReplacerBeanName;
      this.typeIdentifiers.addAll(typeIdentifiers);
   }

   public String getMethodReplacerBeanName() {
      return this.methodReplacerBeanName;
   }

   public void addTypeIdentifier(String identifier) {
      this.typeIdentifiers.add(identifier);
   }

   public List<String> getTypeIdentifiers() {
      return Collections.unmodifiableList(this.typeIdentifiers);
   }

   @Override
   public boolean matches(Method method) {
      if (!method.getName().equals(this.getMethodName())) {
         return false;
      }

      if (!this.isOverloaded()) {
         return true;
      }

      if (this.typeIdentifiers.size() != method.getParameterCount()) {
         return false;
      }

      Class<?>[] parameterTypes = method.getParameterTypes();

      for (int i = 0; i < this.typeIdentifiers.size(); i++) {
         String identifier = this.typeIdentifiers.get(i);
         if (!parameterTypes[i].getName().contains(identifier)) {
            return false;
         }
      }

      return true;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return other instanceof ReplaceOverride that
         && super.equals(that)
         && this.methodReplacerBeanName.equals(that.methodReplacerBeanName)
         && this.typeIdentifiers.equals(that.typeIdentifiers);
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.methodReplacerBeanName, this.typeIdentifiers);
   }

   @Override
   public String toString() {
      return "ReplaceOverride for method '" + this.getMethodName() + "'";
   }
}
