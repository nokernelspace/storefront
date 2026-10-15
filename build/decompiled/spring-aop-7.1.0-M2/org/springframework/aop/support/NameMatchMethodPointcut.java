package org.springframework.aop.support;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.util.PatternMatchUtils;

public class NameMatchMethodPointcut extends StaticMethodMatcherPointcut implements Serializable {
   private List<String> mappedNamePatterns = new ArrayList<>();

   public void setMappedName(String mappedNamePattern) {
      this.setMappedNames(mappedNamePattern);
   }

   public void setMappedNames(String... mappedNamePatterns) {
      this.mappedNamePatterns = new ArrayList<>(Arrays.asList(mappedNamePatterns));
   }

   public NameMatchMethodPointcut addMethodName(String mappedNamePattern) {
      this.mappedNamePatterns.add(mappedNamePattern);
      return this;
   }

   @Override
   public boolean matches(Method method, Class<?> targetClass) {
      for (String mappedNamePattern : this.mappedNamePatterns) {
         if (mappedNamePattern.equals(method.getName()) || this.isMatch(method.getName(), mappedNamePattern)) {
            return true;
         }
      }

      return false;
   }

   protected boolean isMatch(String methodName, String mappedNamePattern) {
      return PatternMatchUtils.simpleMatch(mappedNamePattern, methodName);
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof NameMatchMethodPointcut that && this.mappedNamePatterns.equals(that.mappedNamePatterns);
   }

   @Override
   public int hashCode() {
      return this.mappedNamePatterns.hashCode();
   }

   @Override
   public String toString() {
      return this.getClass().getName() + ": " + this.mappedNamePatterns;
   }
}
