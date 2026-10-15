package org.springframework.aop.aspectj.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.StringTokenizer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.AjType;
import org.aspectj.lang.reflect.AjTypeSystem;
import org.aspectj.lang.reflect.PerClauseKind;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.SpringProperties;
import org.springframework.core.annotation.AnnotationUtils;

public abstract class AbstractAspectJAdvisorFactory implements AspectJAdvisorFactory {
   private static final Class<?>[] ASPECTJ_ANNOTATION_CLASSES = new Class[]{
      Pointcut.class, Around.class, Before.class, After.class, AfterReturning.class, AfterThrowing.class
   };
   private static final String AJC_MAGIC = "ajc$";
   public static final String IGNORE_AJC_PROPERTY_NAME = "spring.aop.ajc.ignore";
   private static final boolean shouldIgnoreAjcCompiledAspects = SpringProperties.getFlag("spring.aop.ajc.ignore");
   protected final Log logger = LogFactory.getLog(this.getClass());
   protected final ParameterNameDiscoverer parameterNameDiscoverer = new AbstractAspectJAdvisorFactory.AspectJAnnotationParameterNameDiscoverer();

   @Override
   public boolean isAspect(Class<?> clazz) {
      return AnnotationUtils.findAnnotation(clazz, Aspect.class) != null && (!shouldIgnoreAjcCompiledAspects || !compiledByAjc(clazz));
   }

   @Override
   public void validate(Class<?> aspectClass) throws AopConfigException {
      AjType<?> ajType = AjTypeSystem.getAjType(aspectClass);
      if (!ajType.isAspect()) {
         throw new NotAnAtAspectException(aspectClass);
      }

      if (ajType.getPerClause().getKind() == PerClauseKind.PERCFLOW) {
         throw new AopConfigException(aspectClass.getName() + " uses percflow instantiation model: This is not supported in Spring AOP.");
      }

      if (ajType.getPerClause().getKind() == PerClauseKind.PERCFLOWBELOW) {
         throw new AopConfigException(aspectClass.getName() + " uses percflowbelow instantiation model: This is not supported in Spring AOP.");
      }
   }

   protected static AbstractAspectJAdvisorFactory.@Nullable AspectJAnnotation findAspectJAnnotationOnMethod(Method method) {
      for (Class<?> annotationType : ASPECTJ_ANNOTATION_CLASSES) {
         AbstractAspectJAdvisorFactory.AspectJAnnotation annotation = findAnnotation(method, (Class<? extends Annotation>)annotationType);
         if (annotation != null) {
            return annotation;
         }
      }

      return null;
   }

   private static AbstractAspectJAdvisorFactory.@Nullable AspectJAnnotation findAnnotation(Method method, Class<? extends Annotation> annotationType) {
      Annotation annotation = AnnotationUtils.findAnnotation(method, annotationType);
      return annotation != null ? new AbstractAspectJAdvisorFactory.AspectJAnnotation(annotation) : null;
   }

   private static boolean compiledByAjc(Class<?> clazz) {
      for (Field field : clazz.getDeclaredFields()) {
         if (field.getName().startsWith("ajc$")) {
            return true;
         }
      }

      return false;
   }

   protected static class AspectJAnnotation {
      private static final String[] EXPRESSION_ATTRIBUTES = new String[]{"pointcut", "value"};
      private static final Map<Class<?>, AbstractAspectJAdvisorFactory.AspectJAnnotationType> annotationTypeMap = Map.of(
         Pointcut.class,
         AbstractAspectJAdvisorFactory.AspectJAnnotationType.AtPointcut,
         Around.class,
         AbstractAspectJAdvisorFactory.AspectJAnnotationType.AtAround,
         Before.class,
         AbstractAspectJAdvisorFactory.AspectJAnnotationType.AtBefore,
         After.class,
         AbstractAspectJAdvisorFactory.AspectJAnnotationType.AtAfter,
         AfterReturning.class,
         AbstractAspectJAdvisorFactory.AspectJAnnotationType.AtAfterReturning,
         AfterThrowing.class,
         AbstractAspectJAdvisorFactory.AspectJAnnotationType.AtAfterThrowing
      );
      private final Annotation annotation;
      private final AbstractAspectJAdvisorFactory.AspectJAnnotationType annotationType;
      private final String pointcutExpression;
      private final String argumentNames;

      public AspectJAnnotation(Annotation annotation) {
         this.annotation = annotation;
         this.annotationType = this.determineAnnotationType(annotation);

         try {
            this.pointcutExpression = this.resolvePointcutExpression(annotation);
            this.argumentNames = AnnotationUtils.getValue(annotation, "argNames") instanceof String names ? names : "";
         } catch (Exception ex) {
            throw new IllegalArgumentException(annotation + " is not a valid AspectJ annotation", ex);
         }
      }

      private AbstractAspectJAdvisorFactory.AspectJAnnotationType determineAnnotationType(Annotation annotation) {
         AbstractAspectJAdvisorFactory.AspectJAnnotationType type = annotationTypeMap.get(annotation.annotationType());
         if (type != null) {
            return type;
         } else {
            throw new IllegalStateException("Unknown annotation type: " + annotation);
         }
      }

      private String resolvePointcutExpression(Annotation annotation) {
         for (String attributeName : EXPRESSION_ATTRIBUTES) {
            if (AnnotationUtils.getValue(annotation, attributeName) instanceof String str && !str.isEmpty()) {
               return str;
            }
         }

         throw new IllegalStateException("Failed to resolve pointcut expression in: " + annotation);
      }

      public AbstractAspectJAdvisorFactory.AspectJAnnotationType getAnnotationType() {
         return this.annotationType;
      }

      public Annotation getAnnotation() {
         return this.annotation;
      }

      public String getPointcutExpression() {
         return this.pointcutExpression;
      }

      public String getArgumentNames() {
         return this.argumentNames;
      }

      @Override
      public String toString() {
         return this.annotation.toString();
      }
   }

   private static class AspectJAnnotationParameterNameDiscoverer implements ParameterNameDiscoverer {
      private static final String[] EMPTY_ARRAY = new String[0];

      public String @Nullable [] getParameterNames(Method method) {
         if (method.getParameterCount() == 0) {
            return EMPTY_ARRAY;
         }

         AbstractAspectJAdvisorFactory.AspectJAnnotation annotation = AbstractAspectJAdvisorFactory.findAspectJAnnotationOnMethod(method);
         if (annotation == null) {
            return null;
         }

         StringTokenizer nameTokens = new StringTokenizer(annotation.getArgumentNames(), ",");
         int numTokens = nameTokens.countTokens();
         if (numTokens <= 0) {
            return null;
         }

         String[] names = new String[numTokens];

         for (int i = 0; i < names.length; i++) {
            names[i] = nameTokens.nextToken();
         }

         return names;
      }

      public @Nullable String @Nullable [] getParameterNames(Constructor<?> ctor) {
         throw new UnsupportedOperationException("Spring AOP cannot handle constructor advice");
      }
   }

   protected enum AspectJAnnotationType {
      AtPointcut,
      AtAround,
      AtBefore,
      AtAfter,
      AtAfterReturning,
      AtAfterThrowing;
   }
}
