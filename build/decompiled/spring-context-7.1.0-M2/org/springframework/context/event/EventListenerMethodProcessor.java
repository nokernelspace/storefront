package org.springframework.context.event;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.framework.autoproxy.AutoProxyUtils;
import org.springframework.aop.scope.ScopedObject;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.CollectionUtils;

public class EventListenerMethodProcessor implements SmartInitializingSingleton, ApplicationContextAware, BeanFactoryPostProcessor {
   protected final Log logger = LogFactory.getLog(this.getClass());
   private @Nullable ConfigurableApplicationContext applicationContext;
   private @Nullable ConfigurableListableBeanFactory beanFactory;
   private @Nullable List<EventListenerFactory> eventListenerFactories;
   private final StandardEvaluationContext originalEvaluationContext;
   private final @Nullable EventExpressionEvaluator evaluator;
   private final Set<Class<?>> nonAnnotatedClasses = ConcurrentHashMap.newKeySet(64);

   public EventListenerMethodProcessor() {
      this.originalEvaluationContext = new StandardEvaluationContext();
      this.evaluator = new EventExpressionEvaluator(this.originalEvaluationContext);
   }

   @Override
   public void setApplicationContext(ApplicationContext applicationContext) {
      Assert.isTrue(applicationContext instanceof ConfigurableApplicationContext, "ApplicationContext does not implement ConfigurableApplicationContext");
      this.applicationContext = (ConfigurableApplicationContext)applicationContext;
   }

   public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
      this.beanFactory = beanFactory;
      this.originalEvaluationContext.setBeanResolver(new BeanFactoryResolver(this.beanFactory));
      Map<String, EventListenerFactory> beans = beanFactory.getBeansOfType(EventListenerFactory.class, false, false);
      List<EventListenerFactory> factories = new ArrayList<>(beans.values());
      AnnotationAwareOrderComparator.sort(factories);
      this.eventListenerFactories = factories;
   }

   public void afterSingletonsInstantiated() {
      ConfigurableListableBeanFactory beanFactory = this.beanFactory;
      Assert.state(beanFactory != null, "No ConfigurableListableBeanFactory set");
      String[] beanNames = beanFactory.getBeanNamesForType(Object.class);

      for (String beanName : beanNames) {
         if (!ScopedProxyUtils.isScopedTarget(beanName)) {
            Class<?> type = null;

            try {
               type = AutoProxyUtils.determineTargetClass(beanFactory, beanName);
            } catch (Throwable ex) {
               if (this.logger.isDebugEnabled()) {
                  this.logger.debug("Could not resolve target class for bean with name '" + beanName + "'", ex);
               }
            }

            if (type != null) {
               if (ScopedObject.class.isAssignableFrom(type)) {
                  try {
                     Class<?> targetClass = AutoProxyUtils.determineTargetClass(beanFactory, ScopedProxyUtils.getTargetBeanName(beanName));
                     if (targetClass != null) {
                        type = targetClass;
                     }
                  } catch (Throwable ex) {
                     if (this.logger.isDebugEnabled()) {
                        this.logger.debug("Could not resolve target bean for scoped proxy '" + beanName + "'", ex);
                     }
                  }
               }

               try {
                  this.processBean(beanName, type);
               } catch (Throwable ex) {
                  throw new BeanInitializationException(
                     "Failed to process @EventListener annotation on bean with name '" + beanName + "': " + ex.getMessage(), ex
                  );
               }
            }
         }
      }
   }

   private void processBean(final String beanName, final Class<?> targetType) {
      if (!this.nonAnnotatedClasses.contains(targetType)
         && AnnotationUtils.isCandidateClass(targetType, EventListener.class)
         && !isSpringContainerClass(targetType)) {
         Map<Method, EventListener> annotatedMethods = null;

         try {
            annotatedMethods = MethodIntrospector.selectMethods(
               targetType, methodx -> (EventListener)AnnotatedElementUtils.findMergedAnnotation(methodx, EventListener.class)
            );
         } catch (Throwable ex) {
            if (this.logger.isDebugEnabled()) {
               this.logger.debug("Could not resolve methods for bean with name '" + beanName + "'", ex);
            }
         }

         if (CollectionUtils.isEmpty(annotatedMethods)) {
            this.nonAnnotatedClasses.add(targetType);
            if (this.logger.isTraceEnabled()) {
               this.logger.trace("No @EventListener annotations found on bean class: " + targetType.getName());
            }
         } else {
            ConfigurableApplicationContext context = this.applicationContext;
            Assert.state(context != null, "No ApplicationContext set");
            List<EventListenerFactory> factories = this.eventListenerFactories;
            Assert.state(factories != null, "EventListenerFactory List not initialized");

            for (Method method : annotatedMethods.keySet()) {
               for (EventListenerFactory factory : factories) {
                  if (factory.supportsMethod(method)) {
                     Method methodToUse = AopUtils.selectInvocableMethod(method, context.getType(beanName));
                     ApplicationListener<?> applicationListener = factory.createApplicationListener(beanName, targetType, methodToUse);
                     if (applicationListener instanceof ApplicationListenerMethodAdapter alma) {
                        alma.init(context, this.evaluator);
                     }

                     context.addApplicationListener(applicationListener);
                     break;
                  }
               }
            }

            if (this.logger.isDebugEnabled()) {
               this.logger.debug(annotatedMethods.size() + " @EventListener methods processed on bean '" + beanName + "': " + annotatedMethods);
            }
         }
      }
   }

   private static boolean isSpringContainerClass(Class<?> clazz) {
      return clazz.getName().startsWith("org.springframework.") && !AnnotatedElementUtils.isAnnotated(ClassUtils.getUserClass(clazz), Component.class);
   }
}
