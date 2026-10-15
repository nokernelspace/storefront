package org.springframework.aop.aspectj.annotation;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.aspectj.lang.reflect.PerClauseKind;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJProxyUtils;
import org.springframework.aop.aspectj.SimpleAspectInstanceFactory;
import org.springframework.aop.framework.ProxyCreatorSupport;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;

public class AspectJProxyFactory extends ProxyCreatorSupport {
   private static final Map<Class<?>, Object> aspectCache = new ConcurrentHashMap<>();
   private final AspectJAdvisorFactory aspectFactory = new ReflectiveAspectJAdvisorFactory();

   public AspectJProxyFactory() {
   }

   public AspectJProxyFactory(Object target) {
      Assert.notNull(target, "Target object must not be null");
      this.setInterfaces(ClassUtils.getAllInterfaces(target));
      this.setTarget(target);
   }

   public AspectJProxyFactory(Class<?>... interfaces) {
      this.setInterfaces(interfaces);
   }

   public void addAspect(Object aspectInstance) {
      Class<?> aspectClass = aspectInstance.getClass();
      String aspectName = aspectClass.getName();
      AspectMetadata am = this.createAspectMetadata(aspectClass, aspectName);
      if (am.getAjType().getPerClause().getKind() != PerClauseKind.SINGLETON) {
         throw new IllegalArgumentException("Aspect class [" + aspectClass.getName() + "] does not define a singleton aspect");
      }

      this.addAdvisorsFromAspectInstanceFactory(new SingletonMetadataAwareAspectInstanceFactory(aspectInstance, aspectName));
   }

   public void addAspect(Class<?> aspectClass) {
      String aspectName = aspectClass.getName();
      AspectMetadata am = this.createAspectMetadata(aspectClass, aspectName);
      MetadataAwareAspectInstanceFactory instanceFactory = this.createAspectInstanceFactory(am, aspectClass, aspectName);
      this.addAdvisorsFromAspectInstanceFactory(instanceFactory);
   }

   private void addAdvisorsFromAspectInstanceFactory(MetadataAwareAspectInstanceFactory instanceFactory) {
      List<Advisor> advisors = this.aspectFactory.getAdvisors(instanceFactory);
      Class<?> targetClass = this.getTargetClass();
      Assert.state(targetClass != null, "Unresolvable target class");
      advisors = AopUtils.findAdvisorsThatCanApply(advisors, targetClass);
      AspectJProxyUtils.makeAdvisorChainAspectJCapableIfNecessary(advisors);
      AnnotationAwareOrderComparator.sort(advisors);
      this.addAdvisors(advisors);
   }

   private AspectMetadata createAspectMetadata(Class<?> aspectClass, String aspectName) {
      AspectMetadata am = new AspectMetadata(aspectClass, aspectName);
      if (!am.getAjType().isAspect()) {
         throw new IllegalArgumentException("Class [" + aspectClass.getName() + "] is not a valid aspect type");
      } else {
         return am;
      }
   }

   private MetadataAwareAspectInstanceFactory createAspectInstanceFactory(AspectMetadata am, Class<?> aspectClass, String aspectName) {
      MetadataAwareAspectInstanceFactory instanceFactory;
      if (am.getAjType().getPerClause().getKind() == PerClauseKind.SINGLETON) {
         Object instance = this.getSingletonAspectInstance(aspectClass);
         instanceFactory = new SingletonMetadataAwareAspectInstanceFactory(instance, aspectName);
      } else {
         instanceFactory = new SimpleMetadataAwareAspectInstanceFactory(aspectClass, aspectName);
      }

      return instanceFactory;
   }

   private Object getSingletonAspectInstance(Class<?> aspectClass) {
      return aspectCache.computeIfAbsent(aspectClass, clazz -> new SimpleAspectInstanceFactory((Class<?>)clazz).getAspectInstance());
   }

   public <T> T getProxy() {
      return (T)this.createAopProxy().getProxy();
   }

   public <T> T getProxy(ClassLoader classLoader) {
      return (T)this.createAopProxy().getProxy(classLoader);
   }
}
