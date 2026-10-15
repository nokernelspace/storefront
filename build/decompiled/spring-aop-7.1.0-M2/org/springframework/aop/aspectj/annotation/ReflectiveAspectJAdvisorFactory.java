package org.springframework.aop.aspectj.annotation;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.aopalliance.aop.Advice;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.DeclareParents;
import org.aspectj.lang.annotation.Pointcut;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.Advisor;
import org.springframework.aop.MethodBeforeAdvice;
import org.springframework.aop.aspectj.AbstractAspectJAdvice;
import org.springframework.aop.aspectj.AspectJAfterAdvice;
import org.springframework.aop.aspectj.AspectJAfterReturningAdvice;
import org.springframework.aop.aspectj.AspectJAfterThrowingAdvice;
import org.springframework.aop.aspectj.AspectJAroundAdvice;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.aspectj.AspectJMethodBeforeAdvice;
import org.springframework.aop.aspectj.DeclareParentsAdvisor;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.convert.converter.ConvertingComparator;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.ReflectionUtils.MethodFilter;
import org.springframework.util.comparator.InstanceComparator;

public class ReflectiveAspectJAdvisorFactory extends AbstractAspectJAdvisorFactory implements Serializable {
   private static final MethodFilter adviceMethodFilter = ReflectionUtils.USER_DECLARED_METHODS
      .and(method -> AnnotationUtils.getAnnotation(method, Pointcut.class) == null);
   private static final Comparator<Method> adviceMethodComparator;
   private final @Nullable BeanFactory beanFactory;

   public ReflectiveAspectJAdvisorFactory() {
      this(null);
   }

   public ReflectiveAspectJAdvisorFactory(@Nullable BeanFactory beanFactory) {
      this.beanFactory = beanFactory;
   }

   @Override
   public List<Advisor> getAdvisors(MetadataAwareAspectInstanceFactory aspectInstanceFactory) {
      Class<?> aspectClass = aspectInstanceFactory.getAspectMetadata().getAspectClass();
      String aspectName = aspectInstanceFactory.getAspectMetadata().getAspectName();
      this.validate(aspectClass);
      MetadataAwareAspectInstanceFactory lazySingletonAspectInstanceFactory = new LazySingletonAspectInstanceFactoryDecorator(aspectInstanceFactory);
      List<Advisor> advisors = new ArrayList<>();

      for (Method method : this.getAdvisorMethods(aspectClass)) {
         if (method.equals(ClassUtils.getMostSpecificMethod(method, aspectClass))) {
            Advisor advisor = this.getAdvisor(method, lazySingletonAspectInstanceFactory, 0, aspectName);
            if (advisor != null) {
               advisors.add(advisor);
            }
         }
      }

      if (!advisors.isEmpty() && lazySingletonAspectInstanceFactory.getAspectMetadata().isLazilyInstantiated()) {
         Advisor instantiationAdvisor = new ReflectiveAspectJAdvisorFactory.SyntheticInstantiationAdvisor(lazySingletonAspectInstanceFactory);
         advisors.add(0, instantiationAdvisor);
      }

      for (Field field : aspectClass.getDeclaredFields()) {
         Advisor advisor = this.getDeclareParentsAdvisor(field);
         if (advisor != null) {
            advisors.add(advisor);
         }
      }

      return advisors;
   }

   private List<Method> getAdvisorMethods(Class<?> aspectClass) {
      List<Method> methods = new ArrayList<>();
      ReflectionUtils.doWithMethods(aspectClass, methods::add, adviceMethodFilter);
      if (methods.size() > 1) {
         methods.sort(adviceMethodComparator);
      }

      return methods;
   }

   private @Nullable Advisor getDeclareParentsAdvisor(Field introductionField) {
      DeclareParents declareParents = introductionField.getAnnotation(DeclareParents.class);
      if (declareParents == null) {
         return null;
      } else if (DeclareParents.class == declareParents.defaultImpl()) {
         throw new IllegalStateException("'defaultImpl' attribute must be set on DeclareParents");
      } else {
         return new DeclareParentsAdvisor(introductionField.getType(), declareParents.value(), declareParents.defaultImpl());
      }
   }

   @Override
   public @Nullable Advisor getAdvisor(
      Method candidateAdviceMethod, MetadataAwareAspectInstanceFactory aspectInstanceFactory, int declarationOrderInAspect, String aspectName
   ) {
      this.validate(aspectInstanceFactory.getAspectMetadata().getAspectClass());
      AspectJExpressionPointcut expressionPointcut = this.getPointcut(candidateAdviceMethod, aspectInstanceFactory.getAspectMetadata().getAspectClass());
      if (expressionPointcut == null) {
         return null;
      }

      try {
         return new InstantiationModelAwarePointcutAdvisorImpl(
            expressionPointcut, candidateAdviceMethod, this, aspectInstanceFactory, declarationOrderInAspect, aspectName
         );
      } catch (IllegalArgumentException | IllegalStateException ex) {
         if (this.logger.isDebugEnabled()) {
            this.logger.debug("Ignoring incompatible advice method: " + candidateAdviceMethod, ex);
         }

         return null;
      }
   }

   private @Nullable AspectJExpressionPointcut getPointcut(Method candidateAdviceMethod, Class<?> candidateAspectClass) {
      AbstractAspectJAdvisorFactory.AspectJAnnotation aspectJAnnotation = AbstractAspectJAdvisorFactory.findAspectJAnnotationOnMethod(candidateAdviceMethod);
      if (aspectJAnnotation == null) {
         return null;
      }

      AspectJExpressionPointcut ajexp = new AspectJExpressionPointcut(candidateAspectClass, new String[0], new Class[0]);
      ajexp.setExpression(aspectJAnnotation.getPointcutExpression());
      if (this.beanFactory != null) {
         ajexp.setBeanFactory(this.beanFactory);
      }

      return ajexp;
   }

   @Override
   public Advice getAdvice(
      Method candidateAdviceMethod,
      AspectJExpressionPointcut expressionPointcut,
      MetadataAwareAspectInstanceFactory aspectInstanceFactory,
      int declarationOrder,
      String aspectName
   ) {
      Class<?> candidateAspectClass = aspectInstanceFactory.getAspectMetadata().getAspectClass();
      this.validate(candidateAspectClass);
      AbstractAspectJAdvisorFactory.AspectJAnnotation aspectJAnnotation = AbstractAspectJAdvisorFactory.findAspectJAnnotationOnMethod(candidateAdviceMethod);
      if (aspectJAnnotation == null) {
         return null;
      }

      if (!this.isAspect(candidateAspectClass)) {
         throw new AopConfigException(
            "Advice must be declared inside an aspect type: Offending method '" + candidateAdviceMethod + "' in class [" + candidateAspectClass.getName() + "]"
         );
      }

      if (this.logger.isDebugEnabled()) {
         this.logger.debug("Found AspectJ method: " + candidateAdviceMethod);
      }

      AbstractAspectJAdvice springAdvice;
      switch (aspectJAnnotation.getAnnotationType()) {
         case AtPointcut:
            if (this.logger.isDebugEnabled()) {
               this.logger.debug("Processing pointcut '" + candidateAdviceMethod.getName() + "'");
            }

            return null;
         case AtAround:
            springAdvice = new AspectJAroundAdvice(candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
            break;
         case AtBefore:
            springAdvice = new AspectJMethodBeforeAdvice(candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
            break;
         case AtAfter:
            springAdvice = new AspectJAfterAdvice(candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
            break;
         case AtAfterReturning:
            springAdvice = new AspectJAfterReturningAdvice(candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
            AfterReturning afterReturningAnnotation = (AfterReturning)aspectJAnnotation.getAnnotation();
            if (StringUtils.hasText(afterReturningAnnotation.returning())) {
               springAdvice.setReturningName(afterReturningAnnotation.returning());
            }
            break;
         case AtAfterThrowing:
            springAdvice = new AspectJAfterThrowingAdvice(candidateAdviceMethod, expressionPointcut, aspectInstanceFactory);
            AfterThrowing afterThrowingAnnotation = (AfterThrowing)aspectJAnnotation.getAnnotation();
            if (StringUtils.hasText(afterThrowingAnnotation.throwing())) {
               springAdvice.setThrowingName(afterThrowingAnnotation.throwing());
            }
            break;
         default:
            throw new UnsupportedOperationException("Unsupported advice type on method: " + candidateAdviceMethod);
      }

      springAdvice.setAspectName(aspectName);
      springAdvice.setDeclarationOrder(declarationOrder);
      String[] argNames = this.parameterNameDiscoverer.getParameterNames(candidateAdviceMethod);
      if (argNames != null) {
         springAdvice.setArgumentNamesFromStringArray(argNames);
      }

      springAdvice.calculateArgumentBindings();
      return springAdvice;
   }

   static {
      Comparator<Method> adviceKindComparator = new ConvertingComparator(
         new InstanceComparator(new Class[]{Around.class, Before.class, After.class, AfterReturning.class, AfterThrowing.class}), method -> {
            AbstractAspectJAdvisorFactory.AspectJAnnotation ann = AbstractAspectJAdvisorFactory.findAspectJAnnotationOnMethod(method);
            return ann != null ? ann.getAnnotation() : null;
         }
      );
      Comparator<Method> methodNameComparator = new ConvertingComparator(Method::getName);
      adviceMethodComparator = adviceKindComparator.thenComparing(methodNameComparator);
   }

   protected static class SyntheticInstantiationAdvisor extends DefaultPointcutAdvisor {
      public SyntheticInstantiationAdvisor(final MetadataAwareAspectInstanceFactory aif) {
         super(aif.getAspectMetadata().getPerClausePointcut(), (MethodBeforeAdvice)(method, args, target) -> aif.getAspectInstance());
      }
   }
}
