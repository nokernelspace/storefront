package org.springframework.aop.aspectj.annotation;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.AjType;
import org.aspectj.lang.reflect.AjTypeSystem;
import org.aspectj.lang.reflect.PerClauseKind;
import org.springframework.aop.Pointcut;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.aspectj.TypePatternClassFilter;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.aop.support.ComposablePointcut;

public class AspectMetadata implements Serializable {
   private final String aspectName;
   private final Class<?> aspectClass;
   private transient AjType<?> ajType;
   private final Pointcut perClausePointcut;

   public AspectMetadata(Class<?> aspectClass, String aspectName) {
      this.aspectName = aspectName;
      Class<?> currClass = aspectClass;
      AjType<?> ajType = null;

      while (currClass != Object.class) {
         AjType<?> ajTypeToCheck = AjTypeSystem.getAjType(currClass);
         if (ajTypeToCheck.isAspect()) {
            ajType = ajTypeToCheck;
            break;
         }

         currClass = currClass.getSuperclass();
      }

      if (ajType == null) {
         throw new IllegalArgumentException("Class '" + aspectClass.getName() + "' is not an @AspectJ aspect");
      }

      if (ajType.getDeclarePrecedence().length > 0) {
         throw new IllegalArgumentException("DeclarePrecedence not presently supported in Spring AOP");
      }

      this.aspectClass = ajType.getJavaClass();
      this.ajType = ajType;
      switch (this.ajType.getPerClause().getKind()) {
         case SINGLETON:
            this.perClausePointcut = Pointcut.TRUE;
            break;
         case PERTARGET:
         case PERTHIS:
            AspectJExpressionPointcut ajexp = new AspectJExpressionPointcut();
            ajexp.setLocation(aspectClass.getName());
            ajexp.setExpression(this.findPerClause(aspectClass));
            ajexp.setPointcutDeclarationScope(aspectClass);
            this.perClausePointcut = ajexp;
            break;
         case PERTYPEWITHIN:
            this.perClausePointcut = new ComposablePointcut(new TypePatternClassFilter(this.findPerClause(aspectClass)));
            break;
         default:
            throw new AopConfigException("PerClause " + ajType.getPerClause().getKind() + " not supported by Spring AOP for " + aspectClass);
      }
   }

   private String findPerClause(Class<?> aspectClass) {
      Aspect ann = aspectClass.getAnnotation(Aspect.class);
      if (ann == null) {
         return "";
      }

      String value = ann.value();
      int beginIndex = value.indexOf(40);
      return beginIndex < 0 ? "" : value.substring(beginIndex + 1, value.length() - 1);
   }

   public AjType<?> getAjType() {
      return this.ajType;
   }

   public Class<?> getAspectClass() {
      return this.aspectClass;
   }

   public String getAspectName() {
      return this.aspectName;
   }

   public Pointcut getPerClausePointcut() {
      return this.perClausePointcut;
   }

   public boolean isPerThisOrPerTarget() {
      PerClauseKind kind = this.getAjType().getPerClause().getKind();
      return kind == PerClauseKind.PERTARGET || kind == PerClauseKind.PERTHIS;
   }

   public boolean isPerTypeWithin() {
      PerClauseKind kind = this.getAjType().getPerClause().getKind();
      return kind == PerClauseKind.PERTYPEWITHIN;
   }

   public boolean isLazilyInstantiated() {
      return this.isPerThisOrPerTarget() || this.isPerTypeWithin();
   }

   private void readObject(ObjectInputStream inputStream) throws IOException, ClassNotFoundException {
      inputStream.defaultReadObject();
      this.ajType = AjTypeSystem.getAjType(this.aspectClass);
   }
}
