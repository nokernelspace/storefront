package org.springframework.aop.support;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;
import org.springframework.util.Assert;
import org.springframework.util.PatternMatchUtils;

public class ControlFlowPointcut implements Pointcut, ClassFilter, MethodMatcher, Serializable {
   protected final Class<?> clazz;
   protected final List<String> methodNamePatterns;
   private final AtomicInteger evaluationCount = new AtomicInteger();

   public ControlFlowPointcut(Class<?> clazz) {
      this(clazz, (String)null);
   }

   public ControlFlowPointcut(Class<?> clazz, @Nullable String methodNamePattern) {
      Assert.notNull(clazz, "Class must not be null");
      this.clazz = clazz;
      this.methodNamePatterns = methodNamePattern != null ? Collections.singletonList(methodNamePattern) : Collections.emptyList();
   }

   public ControlFlowPointcut(Class<?> clazz, String... methodNamePatterns) {
      this(clazz, Arrays.asList(methodNamePatterns));
   }

   public ControlFlowPointcut(Class<?> clazz, List<String> methodNamePatterns) {
      Assert.notNull(clazz, "Class must not be null");
      Assert.notNull(methodNamePatterns, "List of method name patterns must not be null");
      Assert.noNullElements(methodNamePatterns, "List of method name patterns must not contain null elements");
      this.clazz = clazz;
      this.methodNamePatterns = methodNamePatterns.stream().distinct().toList();
   }

   @Override
   public boolean matches(Class<?> clazz) {
      return true;
   }

   @Override
   public boolean matches(Method method, Class<?> targetClass) {
      return true;
   }

   @Override
   public boolean isRuntime() {
      return true;
   }

   @Override
   public boolean matches(Method method, Class<?> targetClass, @Nullable Object... args) {
      this.incrementEvaluationCount();

      for (StackTraceElement element : new Throwable().getStackTrace()) {
         if (element.getClassName().equals(this.clazz.getName())) {
            if (this.methodNamePatterns.isEmpty()) {
               return true;
            }

            String methodName = element.getMethodName();

            for (int i = 0; i < this.methodNamePatterns.size(); i++) {
               if (this.isMatch(methodName, i)) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   public int getEvaluations() {
      return this.evaluationCount.get();
   }

   protected final void incrementEvaluationCount() {
      this.evaluationCount.incrementAndGet();
   }

   protected boolean isMatch(String methodName, int patternIndex) {
      String methodNamePattern = this.methodNamePatterns.get(patternIndex);
      return this.isMatch(methodName, methodNamePattern);
   }

   protected boolean isMatch(String methodName, String methodNamePattern) {
      return methodName.equals(methodNamePattern) || PatternMatchUtils.simpleMatch(methodNamePattern, methodName);
   }

   @Override
   public ClassFilter getClassFilter() {
      return this;
   }

   @Override
   public MethodMatcher getMethodMatcher() {
      return this;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof ControlFlowPointcut that && this.clazz.equals(that.clazz) && this.methodNamePatterns.equals(that.methodNamePatterns);
   }

   @Override
   public int hashCode() {
      int code = this.clazz.hashCode();
      return 37 * code + this.methodNamePatterns.hashCode();
   }

   @Override
   public String toString() {
      return this.getClass().getName() + ": class = " + this.clazz.getName() + "; methodNamePatterns = " + this.methodNamePatterns;
   }
}
