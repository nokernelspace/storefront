package org.springframework.aop.framework;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.aopalliance.aop.Advice;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.Advisor;
import org.springframework.aop.DynamicIntroductionAdvice;
import org.springframework.aop.IntroductionAdvisor;
import org.springframework.aop.IntroductionInfo;
import org.springframework.aop.Pointcut;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.SpringProxy;
import org.springframework.aop.TargetSource;
import org.springframework.aop.support.DefaultIntroductionAdvisor;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.target.EmptyTargetSource;
import org.springframework.aop.target.SingletonTargetSource;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

public class AdvisedSupport extends ProxyConfig implements Advised {
   private static final long serialVersionUID = 2651364800145442165L;
   private static final Advisor[] EMPTY_ADVISOR_ARRAY = new Advisor[0];
   public static final TargetSource EMPTY_TARGET_SOURCE = EmptyTargetSource.INSTANCE;
   TargetSource targetSource = EMPTY_TARGET_SOURCE;
   private boolean preFiltered = false;
   private AdvisorChainFactory advisorChainFactory = DefaultAdvisorChainFactory.INSTANCE;
   private List<Class<?>> interfaces = new ArrayList<>();
   private List<Advisor> advisors = new ArrayList<>();
   private List<Advisor> advisorKey = this.advisors;
   private transient @Nullable Map<AdvisedSupport.MethodCacheKey, List<Object>> methodCache;
   private transient volatile @Nullable List<Object> cachedInterceptors;
   transient volatile @Nullable Object proxyMetadataCache;

   public AdvisedSupport() {
   }

   public AdvisedSupport(Class<?>... interfaces) {
      this.setInterfaces(interfaces);
   }

   public void setTarget(Object target) {
      this.setTargetSource(new SingletonTargetSource(target));
   }

   @Override
   public void setTargetSource(@Nullable TargetSource targetSource) {
      this.targetSource = targetSource != null ? targetSource : EMPTY_TARGET_SOURCE;
   }

   @Override
   public TargetSource getTargetSource() {
      return this.targetSource;
   }

   public void setTargetClass(@Nullable Class<?> targetClass) {
      this.targetSource = EmptyTargetSource.forClass(targetClass);
   }

   @Override
   public @Nullable Class<?> getTargetClass() {
      return this.targetSource.getTargetClass();
   }

   @Override
   public void setPreFiltered(boolean preFiltered) {
      this.preFiltered = preFiltered;
   }

   @Override
   public boolean isPreFiltered() {
      return this.preFiltered;
   }

   public void setAdvisorChainFactory(AdvisorChainFactory advisorChainFactory) {
      Assert.notNull(advisorChainFactory, "AdvisorChainFactory must not be null");
      this.advisorChainFactory = advisorChainFactory;
   }

   public AdvisorChainFactory getAdvisorChainFactory() {
      return this.advisorChainFactory;
   }

   public void setInterfaces(Class<?>... interfaces) {
      Assert.notNull(interfaces, "Interfaces must not be null");
      this.interfaces.clear();

      for (Class<?> ifc : interfaces) {
         this.addInterface(ifc);
      }
   }

   public void addInterface(Class<?> ifc) {
      Assert.notNull(ifc, "Interface must not be null");
      if (!ifc.isInterface()) {
         throw new IllegalArgumentException("[" + ifc.getName() + "] is not an interface");
      }

      if (!this.interfaces.contains(ifc)) {
         this.interfaces.add(ifc);
         this.adviceChanged();
      }
   }

   public boolean removeInterface(Class<?> ifc) {
      return this.interfaces.remove(ifc);
   }

   @Override
   public Class<?>[] getProxiedInterfaces() {
      return ClassUtils.toClassArray(this.interfaces);
   }

   @Override
   public boolean isInterfaceProxied(Class<?> ifc) {
      for (Class<?> proxyIntf : this.interfaces) {
         if (ifc.isAssignableFrom(proxyIntf)) {
            return true;
         }
      }

      return false;
   }

   boolean hasUserSuppliedInterfaces() {
      for (Class<?> ifc : this.interfaces) {
         if (!SpringProxy.class.isAssignableFrom(ifc) && !this.isAdvisorIntroducedInterface(ifc)) {
            return true;
         }
      }

      return false;
   }

   private boolean isAdvisorIntroducedInterface(Class<?> ifc) {
      for (Advisor advisor : this.advisors) {
         if (advisor instanceof IntroductionAdvisor introductionAdvisor) {
            for (Class<?> introducedInterface : introductionAdvisor.getInterfaces()) {
               if (introducedInterface == ifc) {
                  return true;
               }
            }
         }
      }

      return false;
   }

   @Override
   public final Advisor[] getAdvisors() {
      return this.advisors.toArray(EMPTY_ADVISOR_ARRAY);
   }

   @Override
   public int getAdvisorCount() {
      return this.advisors.size();
   }

   @Override
   public void addAdvisor(Advisor advisor) {
      int pos = this.advisors.size();
      this.addAdvisor(pos, advisor);
   }

   @Override
   public void addAdvisor(int pos, Advisor advisor) throws AopConfigException {
      if (advisor instanceof IntroductionAdvisor introductionAdvisor) {
         this.validateIntroductionAdvisor(introductionAdvisor);
      }

      this.addAdvisorInternal(pos, advisor);
   }

   @Override
   public boolean removeAdvisor(Advisor advisor) {
      int index = this.indexOf(advisor);
      if (index == -1) {
         return false;
      }

      this.removeAdvisor(index);
      return true;
   }

   @Override
   public void removeAdvisor(int index) throws AopConfigException {
      if (this.isFrozen()) {
         throw new AopConfigException("Cannot remove Advisor: Configuration is frozen.");
      }

      if (index >= 0 && index <= this.advisors.size() - 1) {
         Advisor advisor = this.advisors.remove(index);
         if (advisor instanceof IntroductionAdvisor introductionAdvisor) {
            for (Class<?> ifc : introductionAdvisor.getInterfaces()) {
               this.removeInterface(ifc);
            }
         }

         this.adviceChanged();
      } else {
         throw new AopConfigException("Advisor index " + index + " is out of bounds: This configuration only has " + this.advisors.size() + " advisors.");
      }
   }

   @Override
   public int indexOf(Advisor advisor) {
      Assert.notNull(advisor, "Advisor must not be null");
      return this.advisors.indexOf(advisor);
   }

   @Override
   public boolean replaceAdvisor(Advisor a, Advisor b) throws AopConfigException {
      Assert.notNull(a, "Advisor a must not be null");
      Assert.notNull(b, "Advisor b must not be null");
      int index = this.indexOf(a);
      if (index == -1) {
         return false;
      }

      this.removeAdvisor(index);
      this.addAdvisor(index, b);
      return true;
   }

   public void addAdvisors(Advisor... advisors) {
      this.addAdvisors(Arrays.asList(advisors));
   }

   public void addAdvisors(Collection<Advisor> advisors) {
      if (this.isFrozen()) {
         throw new AopConfigException("Cannot add advisor: Configuration is frozen.");
      }

      if (!CollectionUtils.isEmpty(advisors)) {
         for (Advisor advisor : advisors) {
            if (advisor instanceof IntroductionAdvisor introductionAdvisor) {
               this.validateIntroductionAdvisor(introductionAdvisor);
            }

            Assert.notNull(advisor, "Advisor must not be null");
            this.advisors.add(advisor);
         }

         this.adviceChanged();
      }
   }

   private void validateIntroductionAdvisor(IntroductionAdvisor advisor) {
      advisor.validateInterfaces();

      for (Class<?> ifc : advisor.getInterfaces()) {
         this.addInterface(ifc);
      }
   }

   private void addAdvisorInternal(int pos, Advisor advisor) throws AopConfigException {
      Assert.notNull(advisor, "Advisor must not be null");
      if (this.isFrozen()) {
         throw new AopConfigException("Cannot add advisor: Configuration is frozen.");
      }

      if (pos > this.advisors.size()) {
         throw new IllegalArgumentException("Illegal position " + pos + " in advisor list with size " + this.advisors.size());
      }

      this.advisors.add(pos, advisor);
      this.adviceChanged();
   }

   protected final List<Advisor> getAdvisorsInternal() {
      return this.advisors;
   }

   @Override
   public void addAdvice(Advice advice) throws AopConfigException {
      int pos = this.advisors.size();
      this.addAdvice(pos, advice);
   }

   @Override
   public void addAdvice(int pos, Advice advice) throws AopConfigException {
      Assert.notNull(advice, "Advice must not be null");
      if (advice instanceof IntroductionInfo introductionInfo) {
         this.addAdvisor(pos, new DefaultIntroductionAdvisor(advice, introductionInfo));
      } else {
         if (advice instanceof DynamicIntroductionAdvice) {
            throw new AopConfigException("DynamicIntroductionAdvice may only be added as part of IntroductionAdvisor");
         }

         this.addAdvisor(pos, new DefaultPointcutAdvisor(advice));
      }
   }

   @Override
   public boolean removeAdvice(Advice advice) throws AopConfigException {
      int index = this.indexOf(advice);
      if (index == -1) {
         return false;
      }

      this.removeAdvisor(index);
      return true;
   }

   @Override
   public int indexOf(Advice advice) {
      Assert.notNull(advice, "Advice must not be null");

      for (int i = 0; i < this.advisors.size(); i++) {
         Advisor advisor = this.advisors.get(i);
         if (advisor.getAdvice() == advice) {
            return i;
         }
      }

      return -1;
   }

   public boolean adviceIncluded(@Nullable Advice advice) {
      if (advice != null) {
         for (Advisor advisor : this.advisors) {
            if (advisor.getAdvice() == advice) {
               return true;
            }
         }
      }

      return false;
   }

   public int countAdvicesOfType(@Nullable Class<?> adviceClass) {
      int count = 0;
      if (adviceClass != null) {
         for (Advisor advisor : this.advisors) {
            if (adviceClass.isInstance(advisor.getAdvice())) {
               count++;
            }
         }
      }

      return count;
   }

   public List<Object> getInterceptorsAndDynamicInterceptionAdvice(Method method, @Nullable Class<?> targetClass) {
      List<Object> cachedInterceptors;
      if (this.methodCache != null) {
         AdvisedSupport.MethodCacheKey cacheKey = new AdvisedSupport.MethodCacheKey(method);
         cachedInterceptors = this.methodCache.get(cacheKey);
         if (cachedInterceptors == null) {
            cachedInterceptors = this.advisorChainFactory.getInterceptorsAndDynamicInterceptionAdvice(this, method, targetClass);
            this.methodCache.put(cacheKey, cachedInterceptors);
         }
      } else {
         cachedInterceptors = this.cachedInterceptors;
         if (cachedInterceptors == null) {
            cachedInterceptors = this.advisorChainFactory.getInterceptorsAndDynamicInterceptionAdvice(this, method, targetClass);
            this.cachedInterceptors = cachedInterceptors;
         }
      }

      return cachedInterceptors;
   }

   protected void adviceChanged() {
      this.methodCache = null;
      this.cachedInterceptors = null;
      this.proxyMetadataCache = null;

      for (Advisor advisor : this.advisors) {
         if (advisor instanceof PointcutAdvisor) {
            this.methodCache = new ConcurrentHashMap<>();
            break;
         }
      }
   }

   protected void copyConfigurationFrom(AdvisedSupport other) {
      this.copyConfigurationFrom(other, other.targetSource, new ArrayList<>(other.advisors));
   }

   protected void copyConfigurationFrom(AdvisedSupport other, TargetSource targetSource, List<Advisor> advisors) {
      this.copyFrom(other);
      this.targetSource = targetSource;
      this.advisorChainFactory = other.advisorChainFactory;
      this.interfaces = new ArrayList<>(other.interfaces);

      for (Advisor advisor : advisors) {
         if (advisor instanceof IntroductionAdvisor introductionAdvisor) {
            this.validateIntroductionAdvisor(introductionAdvisor);
         }

         Assert.notNull(advisor, "Advisor must not be null");
         this.advisors.add(advisor);
      }

      this.adviceChanged();
   }

   AdvisedSupport getConfigurationOnlyCopy() {
      AdvisedSupport copy = new AdvisedSupport();
      copy.copyFrom(this);
      copy.targetSource = EmptyTargetSource.forClass(this.getTargetClass(), this.getTargetSource().isStatic());
      copy.preFiltered = this.preFiltered;
      copy.advisorChainFactory = this.advisorChainFactory;
      copy.interfaces = new ArrayList<>(this.interfaces);
      copy.advisors = new ArrayList<>(this.advisors);
      copy.advisorKey = new ArrayList<>(this.advisors.size());

      for (Advisor advisor : this.advisors) {
         copy.advisorKey.add(new AdvisedSupport.AdvisorKeyEntry(advisor));
      }

      copy.methodCache = this.methodCache;
      copy.cachedInterceptors = this.cachedInterceptors;
      copy.proxyMetadataCache = this.proxyMetadataCache;
      return copy;
   }

   void reduceToAdvisorKey() {
      this.advisors = this.advisorKey;
      this.methodCache = null;
      this.cachedInterceptors = null;
      this.proxyMetadataCache = null;
   }

   Object getAdvisorKey() {
      return this.advisorKey;
   }

   @Override
   public String toProxyConfigString() {
      return this.toString();
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder(this.getClass().getName());
      sb.append(": ").append(this.interfaces.size()).append(" interfaces ");
      sb.append(ClassUtils.classNamesToString(this.interfaces)).append("; ");
      sb.append(this.advisors.size()).append(" advisors ");
      sb.append(this.advisors).append("; ");
      sb.append("targetSource [").append(this.targetSource).append("]; ");
      sb.append(super.toString());
      return sb.toString();
   }

   private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
      ois.defaultReadObject();
      this.adviceChanged();
   }

   private static final class AdvisorKeyEntry implements Advisor {
      private final Class<?> adviceType;
      private final @Nullable String classFilterKey;
      private final @Nullable String methodMatcherKey;

      public AdvisorKeyEntry(Advisor advisor) {
         this.adviceType = advisor.getAdvice().getClass();
         if (advisor instanceof PointcutAdvisor pointcutAdvisor) {
            Pointcut pointcut = pointcutAdvisor.getPointcut();
            this.classFilterKey = pointcut.getClassFilter().toString();
            this.methodMatcherKey = pointcut.getMethodMatcher().toString();
         } else {
            this.classFilterKey = null;
            this.methodMatcherKey = null;
         }
      }

      @Override
      public Advice getAdvice() {
         throw new UnsupportedOperationException();
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other
            || other instanceof AdvisedSupport.AdvisorKeyEntry that
               && this.adviceType == that.adviceType
               && ObjectUtils.nullSafeEquals(this.classFilterKey, that.classFilterKey)
               && ObjectUtils.nullSafeEquals(this.methodMatcherKey, that.methodMatcherKey);
      }

      @Override
      public int hashCode() {
         return this.adviceType.hashCode();
      }
   }

   private static final class MethodCacheKey implements Comparable<AdvisedSupport.MethodCacheKey> {
      private final Method method;
      private final int hashCode;

      public MethodCacheKey(Method method) {
         this.method = method;
         this.hashCode = method.hashCode();
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other || other instanceof AdvisedSupport.MethodCacheKey that && (this.method == that.method || this.method.equals(that.method));
      }

      @Override
      public int hashCode() {
         return this.hashCode;
      }

      @Override
      public String toString() {
         return this.method.toString();
      }

      public int compareTo(AdvisedSupport.MethodCacheKey other) {
         int result = this.method.getName().compareTo(other.method.getName());
         if (result == 0) {
            result = this.method.toString().compareTo(other.method.toString());
         }

         return result;
      }
   }
}
