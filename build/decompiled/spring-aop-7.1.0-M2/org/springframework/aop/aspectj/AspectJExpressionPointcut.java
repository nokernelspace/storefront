package org.springframework.aop.aspectj;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Set;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.util.FuzzyBoolean;
import org.aspectj.weaver.patterns.NamePattern;
import org.aspectj.weaver.reflect.ShadowMatchImpl;
import org.aspectj.weaver.reflect.ReflectionWorld.ReflectionWorldException;
import org.aspectj.weaver.tools.ContextBasedMatcher;
import org.aspectj.weaver.tools.JoinPointMatch;
import org.aspectj.weaver.tools.MatchingContext;
import org.aspectj.weaver.tools.PointcutDesignatorHandler;
import org.aspectj.weaver.tools.PointcutExpression;
import org.aspectj.weaver.tools.PointcutParameter;
import org.aspectj.weaver.tools.PointcutParser;
import org.aspectj.weaver.tools.PointcutPrimitive;
import org.aspectj.weaver.tools.ShadowMatch;
import org.aspectj.weaver.tools.UnsupportedPointcutPrimitiveException;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.IntroductionAwareMethodMatcher;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.aop.framework.autoproxy.ProxyCreationContext;
import org.springframework.aop.interceptor.ExposeInvocationInterceptor;
import org.springframework.aop.support.AbstractExpressionPointcut;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public class AspectJExpressionPointcut extends AbstractExpressionPointcut implements ClassFilter, IntroductionAwareMethodMatcher, BeanFactoryAware {
   private static final String AJC_MAGIC = "ajc$";
   private static final Set<PointcutPrimitive> SUPPORTED_PRIMITIVES = Set.of(
      PointcutPrimitive.EXECUTION,
      PointcutPrimitive.ARGS,
      PointcutPrimitive.REFERENCE,
      PointcutPrimitive.THIS,
      PointcutPrimitive.TARGET,
      PointcutPrimitive.WITHIN,
      PointcutPrimitive.AT_ANNOTATION,
      PointcutPrimitive.AT_WITHIN,
      PointcutPrimitive.AT_ARGS,
      PointcutPrimitive.AT_TARGET
   );
   private static final Log logger = LogFactory.getLog(AspectJExpressionPointcut.class);
   private @Nullable Class<?> pointcutDeclarationScope;
   private boolean aspectCompiledByAjc;
   private String[] pointcutParameterNames = new String[0];
   private Class<?>[] pointcutParameterTypes = new Class[0];
   private @Nullable BeanFactory beanFactory;
   private transient volatile @Nullable ClassLoader pointcutClassLoader;
   private transient volatile @Nullable PointcutExpression pointcutExpression;
   private transient volatile boolean pointcutParsingFailed;

   public AspectJExpressionPointcut() {
   }

   public AspectJExpressionPointcut(Class<?> declarationScope, String[] paramNames, Class<?>[] paramTypes) {
      this.setPointcutDeclarationScope(declarationScope);
      if (paramNames.length != paramTypes.length) {
         throw new IllegalStateException("Number of pointcut parameter names must match number of pointcut parameter types");
      }

      this.pointcutParameterNames = paramNames;
      this.pointcutParameterTypes = paramTypes;
   }

   public void setPointcutDeclarationScope(Class<?> pointcutDeclarationScope) {
      this.pointcutDeclarationScope = pointcutDeclarationScope;
      this.aspectCompiledByAjc = compiledByAjc(pointcutDeclarationScope);
   }

   public void setParameterNames(String... names) {
      this.pointcutParameterNames = names;
   }

   public void setParameterTypes(Class<?>... types) {
      this.pointcutParameterTypes = types;
   }

   public void setBeanFactory(BeanFactory beanFactory) {
      this.beanFactory = beanFactory;
   }

   @Override
   public ClassFilter getClassFilter() {
      this.checkExpression();
      return this;
   }

   @Override
   public MethodMatcher getMethodMatcher() {
      this.checkExpression();
      return this;
   }

   private void checkExpression() {
      if (this.getExpression() == null) {
         throw new IllegalStateException("Must set property 'expression' before attempting to match");
      }
   }

   private PointcutExpression obtainPointcutExpression() {
      PointcutExpression pointcutExpression = this.pointcutExpression;
      if (pointcutExpression == null) {
         ClassLoader pointcutClassLoader = this.determinePointcutClassLoader();
         pointcutExpression = this.buildPointcutExpression(pointcutClassLoader);
         this.pointcutClassLoader = pointcutClassLoader;
         this.pointcutExpression = pointcutExpression;
      }

      return pointcutExpression;
   }

   private @Nullable ClassLoader determinePointcutClassLoader() {
      if (this.beanFactory instanceof ConfigurableBeanFactory cbf) {
         return cbf.getBeanClassLoader();
      } else {
         return this.pointcutDeclarationScope != null ? this.pointcutDeclarationScope.getClassLoader() : ClassUtils.getDefaultClassLoader();
      }
   }

   private PointcutExpression buildPointcutExpression(@Nullable ClassLoader classLoader) {
      PointcutParser parser = this.initializePointcutParser(classLoader);
      PointcutParameter[] pointcutParameters = new PointcutParameter[this.pointcutParameterNames.length];

      for (int i = 0; i < pointcutParameters.length; i++) {
         pointcutParameters[i] = parser.createPointcutParameter(this.pointcutParameterNames[i], this.pointcutParameterTypes[i]);
      }

      return parser.parsePointcutExpression(this.replaceBooleanOperators(this.resolveExpression()), this.pointcutDeclarationScope, pointcutParameters);
   }

   private String resolveExpression() {
      String expression = this.getExpression();
      Assert.state(expression != null, "No expression set");
      return expression;
   }

   private PointcutParser initializePointcutParser(@Nullable ClassLoader classLoader) {
      PointcutParser parser = PointcutParser.getPointcutParserSupportingSpecifiedPrimitivesAndUsingSpecifiedClassLoaderForResolution(
         SUPPORTED_PRIMITIVES, classLoader
      );
      parser.registerPointcutDesignatorHandler(new AspectJExpressionPointcut.BeanPointcutDesignatorHandler());
      return parser;
   }

   private String replaceBooleanOperators(String pcExpr) {
      String result = StringUtils.replace(pcExpr, " and ", " && ");
      result = StringUtils.replace(result, " or ", " || ");
      return StringUtils.replace(result, " not ", " ! ");
   }

   public PointcutExpression getPointcutExpression() {
      return this.obtainPointcutExpression();
   }

   @Override
   public boolean matches(Class<?> targetClass) {
      if (this.pointcutParsingFailed) {
         return false;
      }

      if (this.aspectCompiledByAjc && compiledByAjc(targetClass)) {
         return false;
      }

      try {
         try {
            return this.obtainPointcutExpression().couldMatchJoinPointsInType(targetClass);
         } catch (ReflectionWorldException ex) {
            logger.debug("PointcutExpression matching rejected target class - trying fallback expression", ex);
            PointcutExpression fallbackExpression = this.getFallbackPointcutExpression(targetClass);
            if (fallbackExpression != null) {
               return fallbackExpression.couldMatchJoinPointsInType(targetClass);
            }
         }
      } catch (IllegalArgumentException | IllegalStateException | UnsupportedPointcutPrimitiveException ex) {
         this.pointcutParsingFailed = true;
         if (logger.isDebugEnabled()) {
            logger.debug("Pointcut parser rejected expression [" + this.getExpression() + "]: " + ex);
         }
      } catch (Throwable ex) {
         logger.debug("PointcutExpression matching rejected target class", ex);
      }

      return false;
   }

   @Override
   public boolean matches(Method method, Class<?> targetClass, boolean hasIntroductions) {
      ShadowMatch shadowMatch = this.getTargetShadowMatch(method, targetClass);
      if (shadowMatch.alwaysMatches()) {
         return true;
      }

      if (shadowMatch.neverMatches()) {
         return false;
      }

      if (hasIntroductions) {
         return true;
      }

      RuntimeTestWalker walker = this.getRuntimeTestWalker(shadowMatch);
      return !walker.testsSubtypeSensitiveVars() || walker.testTargetInstanceOfResidue(targetClass);
   }

   @Override
   public boolean matches(Method method, Class<?> targetClass) {
      return this.matches(method, targetClass, false);
   }

   @Override
   public boolean isRuntime() {
      return this.obtainPointcutExpression().mayNeedDynamicTest();
   }

   @Override
   public boolean matches(Method method, Class<?> targetClass, @Nullable Object... args) {
      ShadowMatch shadowMatch = this.getTargetShadowMatch(method, targetClass);
      ProxyMethodInvocation pmi = null;
      Object targetObject = null;
      Object thisObject = null;

      try {
         MethodInvocation curr = ExposeInvocationInterceptor.currentInvocation();
         if (curr.getMethod() == method) {
            targetObject = curr.getThis();
            if (!(curr instanceof ProxyMethodInvocation currPmi)) {
               throw new IllegalStateException("MethodInvocation is not a Spring ProxyMethodInvocation: " + curr);
            }

            pmi = currPmi;
            thisObject = pmi.getProxy();
         }
      } catch (IllegalStateException ex) {
         if (logger.isDebugEnabled()) {
            logger.debug("Could not access current invocation - matching with limited context: " + ex);
         }
      }

      try {
         JoinPointMatch joinPointMatch = shadowMatch.matchesJoinPoint(thisObject, targetObject, args);
         if (pmi != null && thisObject != null) {
            RuntimeTestWalker originalMethodResidueTest = this.getRuntimeTestWalker(this.getShadowMatch(method, method));
            if (!originalMethodResidueTest.testThisInstanceOfResidue(thisObject.getClass())) {
               return false;
            }

            if (joinPointMatch.matches()) {
               this.bindParameters(pmi, joinPointMatch);
            }
         }

         return joinPointMatch.matches();
      } catch (Throwable ex) {
         if (logger.isDebugEnabled()) {
            logger.debug("Failed to evaluate join point for arguments " + Arrays.toString(args) + " - falling back to non-match", ex);
         }

         return false;
      }
   }

   protected @Nullable String getCurrentProxiedBeanName() {
      return ProxyCreationContext.getCurrentProxiedBeanName();
   }

   private @Nullable PointcutExpression getFallbackPointcutExpression(Class<?> targetClass) {
      try {
         ClassLoader classLoader = targetClass.getClassLoader();
         if (classLoader != null && classLoader != this.pointcutClassLoader) {
            return this.buildPointcutExpression(classLoader);
         }
      } catch (Throwable ex) {
         logger.debug("Failed to create fallback PointcutExpression", ex);
      }

      return null;
   }

   private RuntimeTestWalker getRuntimeTestWalker(ShadowMatch shadowMatch) {
      return shadowMatch instanceof AspectJExpressionPointcut.DefensiveShadowMatch defensiveShadowMatch
         ? new RuntimeTestWalker(defensiveShadowMatch.primary)
         : new RuntimeTestWalker(shadowMatch);
   }

   private void bindParameters(ProxyMethodInvocation invocation, JoinPointMatch jpm) {
      invocation.setUserAttribute(this.resolveExpression(), jpm);
   }

   private ShadowMatch getTargetShadowMatch(Method method, Class<?> targetClass) {
      Method targetMethod = AopUtils.getMostSpecificMethod(method, targetClass);
      if (targetMethod.getDeclaringClass().isInterface()
         && targetMethod.getDeclaringClass() != targetClass
         && this.obtainPointcutExpression().getPointcutExpression().contains("." + targetMethod.getName() + "(")) {
         Set<Class<?>> ifcs = ClassUtils.getAllInterfacesForClassAsSet(targetClass);
         if (ifcs.size() > 1) {
            try {
               Class<?> compositeInterface = ClassUtils.createCompositeInterface(ClassUtils.toClassArray(ifcs), targetClass.getClassLoader());
               targetMethod = ClassUtils.getMostSpecificMethod(targetMethod, compositeInterface);
            } catch (IllegalArgumentException var6) {
            }
         }
      }

      return this.getShadowMatch(targetMethod, method);
   }

   private ShadowMatch getShadowMatch(Method targetMethod, Method originalMethod) {
      AspectJExpressionPointcut.ShadowMatchKey key = new AspectJExpressionPointcut.ShadowMatchKey(this, targetMethod);
      ShadowMatch shadowMatch = ShadowMatchUtils.getShadowMatch(key);
      if (shadowMatch == null) {
         PointcutExpression pointcutExpression = this.obtainPointcutExpression();
         synchronized (pointcutExpression) {
            shadowMatch = ShadowMatchUtils.getShadowMatch(key);
            if (shadowMatch != null) {
               return shadowMatch;
            }

            PointcutExpression fallbackExpression = null;
            Method methodToMatch = targetMethod;

            try {
               try {
                  shadowMatch = pointcutExpression.matchesMethodExecution(methodToMatch);
               } catch (ReflectionWorldException ex) {
                  try {
                     fallbackExpression = this.getFallbackPointcutExpression(methodToMatch.getDeclaringClass());
                     if (fallbackExpression != null) {
                        shadowMatch = fallbackExpression.matchesMethodExecution(methodToMatch);
                     }
                  } catch (ReflectionWorldException ex2) {
                     fallbackExpression = null;
                  }
               }

               if (targetMethod != originalMethod
                  && (
                     shadowMatch == null
                        || Proxy.isProxyClass(targetMethod.getDeclaringClass()) && (shadowMatch.neverMatches() || this.containsAnnotationPointcut())
                  )) {
                  methodToMatch = originalMethod;

                  try {
                     shadowMatch = pointcutExpression.matchesMethodExecution(methodToMatch);
                  } catch (ReflectionWorldException ex) {
                     try {
                        fallbackExpression = this.getFallbackPointcutExpression(methodToMatch.getDeclaringClass());
                        if (fallbackExpression != null) {
                           shadowMatch = fallbackExpression.matchesMethodExecution(methodToMatch);
                        }
                     } catch (ReflectionWorldException ex2) {
                        fallbackExpression = null;
                     }
                  }
               }
            } catch (Throwable ex) {
               logger.debug("PointcutExpression matching rejected target method", ex);
               fallbackExpression = null;
            }

            if (shadowMatch == null) {
               shadowMatch = new ShadowMatchImpl(FuzzyBoolean.NO, null, null, null);
            } else if (shadowMatch.maybeMatches() && fallbackExpression != null) {
               shadowMatch = new AspectJExpressionPointcut.DefensiveShadowMatch(shadowMatch, fallbackExpression.matchesMethodExecution(methodToMatch));
            }

            shadowMatch = ShadowMatchUtils.setShadowMatch(key, shadowMatch);
         }
      }

      return shadowMatch;
   }

   private boolean containsAnnotationPointcut() {
      return this.resolveExpression().contains("@annotation");
   }

   private static boolean compiledByAjc(Class<?> clazz) {
      for (Field field : clazz.getDeclaredFields()) {
         if (field.getName().startsWith("ajc$")) {
            return true;
         }
      }

      Class<?> superclass = clazz.getSuperclass();
      return superclass != null && compiledByAjc(superclass);
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof AspectJExpressionPointcut that
            && ObjectUtils.nullSafeEquals(this.getExpression(), that.getExpression())
            && ObjectUtils.nullSafeEquals(this.pointcutDeclarationScope, that.pointcutDeclarationScope)
            && ObjectUtils.nullSafeEquals(this.pointcutParameterNames, that.pointcutParameterNames)
            && ObjectUtils.nullSafeEquals(this.pointcutParameterTypes, that.pointcutParameterTypes);
   }

   @Override
   public int hashCode() {
      return ObjectUtils.nullSafeHash(
         new Object[]{this.getExpression(), this.pointcutDeclarationScope, this.pointcutParameterNames, this.pointcutParameterTypes}
      );
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder("AspectJExpressionPointcut: (");

      for (int i = 0; i < this.pointcutParameterTypes.length; i++) {
         sb.append(this.pointcutParameterTypes[i].getName());
         sb.append(' ');
         sb.append(this.pointcutParameterNames[i]);
         if (i + 1 < this.pointcutParameterTypes.length) {
            sb.append(", ");
         }
      }

      sb.append(") ");
      if (this.getExpression() != null) {
         sb.append(this.getExpression());
      } else {
         sb.append("<pointcut expression not set>");
      }

      return sb.toString();
   }

   private class BeanContextMatcher implements ContextBasedMatcher {
      private final NamePattern expressionPattern;

      public BeanContextMatcher(String expression) {
         this.expressionPattern = new NamePattern(expression);
      }

      @Deprecated(since = "4.0")
      public boolean couldMatchJoinPointsInType(Class someClass) {
         return this.contextMatch(someClass) == org.aspectj.weaver.tools.FuzzyBoolean.YES;
      }

      @Deprecated(since = "4.0")
      public boolean couldMatchJoinPointsInType(Class someClass, MatchingContext context) {
         return this.contextMatch(someClass) == org.aspectj.weaver.tools.FuzzyBoolean.YES;
      }

      public boolean matchesDynamically(MatchingContext context) {
         return true;
      }

      public org.aspectj.weaver.tools.FuzzyBoolean matchesStatically(MatchingContext context) {
         return this.contextMatch(null);
      }

      public boolean mayNeedDynamicTest() {
         return false;
      }

      private org.aspectj.weaver.tools.FuzzyBoolean contextMatch(@Nullable Class<?> targetType) {
         String advisedBeanName = AspectJExpressionPointcut.this.getCurrentProxiedBeanName();
         if (advisedBeanName == null) {
            return org.aspectj.weaver.tools.FuzzyBoolean.MAYBE;
         } else if (BeanFactoryUtils.isGeneratedBeanName(advisedBeanName)) {
            return org.aspectj.weaver.tools.FuzzyBoolean.NO;
         } else if (targetType != null) {
            boolean isFactory = FactoryBean.class.isAssignableFrom(targetType);
            return org.aspectj.weaver.tools.FuzzyBoolean.fromBoolean(this.matchesBean(isFactory ? "&" + advisedBeanName : advisedBeanName));
         } else {
            return org.aspectj.weaver.tools.FuzzyBoolean.fromBoolean(this.matchesBean(advisedBeanName) || this.matchesBean("&" + advisedBeanName));
         }
      }

      private boolean matchesBean(String advisedBeanName) {
         return BeanFactoryAnnotationUtils.isQualifierMatch(this.expressionPattern::matches, advisedBeanName, AspectJExpressionPointcut.this.beanFactory);
      }
   }

   private class BeanPointcutDesignatorHandler implements PointcutDesignatorHandler {
      private static final String BEAN_DESIGNATOR_NAME = "bean";

      public String getDesignatorName() {
         return "bean";
      }

      public ContextBasedMatcher parse(String expression) {
         return AspectJExpressionPointcut.this.new BeanContextMatcher(expression);
      }
   }

   private static class DefensiveShadowMatch implements ShadowMatch {
      private final ShadowMatch primary;
      private final ShadowMatch other;

      public DefensiveShadowMatch(ShadowMatch primary, ShadowMatch other) {
         this.primary = primary;
         this.other = other;
      }

      public boolean alwaysMatches() {
         return this.primary.alwaysMatches();
      }

      public boolean maybeMatches() {
         return this.primary.maybeMatches();
      }

      public boolean neverMatches() {
         return this.primary.neverMatches();
      }

      public JoinPointMatch matchesJoinPoint(Object thisObject, Object targetObject, Object[] args) {
         try {
            return this.primary.matchesJoinPoint(thisObject, targetObject, args);
         } catch (ReflectionWorldException ex) {
            return this.other.matchesJoinPoint(thisObject, targetObject, args);
         }
      }

      public void setMatchingContext(MatchingContext aMatchContext) {
         this.primary.setMatchingContext(aMatchContext);
         this.other.setMatchingContext(aMatchContext);
      }
   }

   private record ShadowMatchKey(AspectJExpressionPointcut expression, Method method) {
   }
}
