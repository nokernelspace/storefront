package org.springframework.aop.support;

import java.io.Serializable;
import org.aopalliance.aop.Advice;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.Pointcut;
import org.springframework.util.ObjectUtils;

public class RegexpMethodPointcutAdvisor extends AbstractGenericPointcutAdvisor {
   private String @Nullable [] patterns;
   private @Nullable AbstractRegexpMethodPointcut pointcut;
   private final Object pointcutMonitor = new RegexpMethodPointcutAdvisor.SerializableMonitor();

   public RegexpMethodPointcutAdvisor() {
   }

   public RegexpMethodPointcutAdvisor(Advice advice) {
      this.setAdvice(advice);
   }

   public RegexpMethodPointcutAdvisor(String pattern, Advice advice) {
      this.setPattern(pattern);
      this.setAdvice(advice);
   }

   public RegexpMethodPointcutAdvisor(String[] patterns, Advice advice) {
      this.setPatterns(patterns);
      this.setAdvice(advice);
   }

   public void setPattern(String pattern) {
      this.setPatterns(pattern);
   }

   public void setPatterns(String... patterns) {
      this.patterns = patterns;
   }

   @Override
   public Pointcut getPointcut() {
      synchronized (this.pointcutMonitor) {
         if (this.pointcut == null) {
            this.pointcut = this.createPointcut();
            if (this.patterns != null) {
               this.pointcut.setPatterns(this.patterns);
            }
         }

         return this.pointcut;
      }
   }

   protected AbstractRegexpMethodPointcut createPointcut() {
      return new JdkRegexpMethodPointcut();
   }

   @Override
   public String toString() {
      return this.getClass().getName() + ": advice [" + this.getAdvice() + "], pointcut patterns " + ObjectUtils.nullSafeToString(this.patterns);
   }

   private static class SerializableMonitor implements Serializable {
   }
}
