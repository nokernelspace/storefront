package org.springframework.web.method;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.aop.scope.ScopedProxyUtils;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.ControllerAdvice;

public class ControllerAdviceBean implements Ordered {
   private final String beanName;
   private final boolean isSingleton;
   private @Nullable Object resolvedBean;
   private final @Nullable Class<?> beanType;
   private final HandlerTypePredicate beanTypePredicate;
   private final BeanFactory beanFactory;
   private @Nullable Integer order;

   public ControllerAdviceBean(String beanName, BeanFactory beanFactory, ControllerAdvice controllerAdvice) {
      Assert.hasText(beanName, "Bean name must contain text");
      Assert.notNull(beanFactory, "BeanFactory must not be null");
      Assert.isTrue(
         beanFactory.containsBean(beanName), () -> "BeanFactory [" + beanFactory + "] does not contain specified controller advice bean '" + beanName + "'"
      );
      Assert.notNull(controllerAdvice, "ControllerAdvice must not be null");
      this.beanName = beanName;
      this.isSingleton = beanFactory.isSingleton(beanName);
      this.beanType = getBeanType(beanName, beanFactory);
      this.beanTypePredicate = createBeanTypePredicate(controllerAdvice);
      this.beanFactory = beanFactory;
   }

   public int getOrder() {
      if (this.order == null) {
         Object resolvedBean = null;
         String targetBeanName = ScopedProxyUtils.getTargetBeanName(this.beanName);
         boolean isScopedProxy = this.beanFactory.containsBean(targetBeanName);
         if (!isScopedProxy && !ScopedProxyUtils.isScopedTarget(this.beanName)) {
            resolvedBean = this.resolveBean();
         }

         if (resolvedBean instanceof Ordered ordered) {
            this.order = ordered.getOrder();
         } else {
            if (this.beanFactory instanceof ConfigurableBeanFactory cbf) {
               try {
                  if (cbf.getMergedBeanDefinition(this.beanName) instanceof RootBeanDefinition rbd) {
                     Method factoryMethod = rbd.getResolvedFactoryMethod();
                     if (factoryMethod != null) {
                        this.order = OrderUtils.getOrder(factoryMethod);
                     }
                  }
               } catch (NoSuchBeanDefinitionException var9) {
               }
            }

            if (this.order == null) {
               if (this.beanType != null) {
                  this.order = OrderUtils.getOrder(this.beanType, Integer.MAX_VALUE);
               } else {
                  this.order = Integer.MAX_VALUE;
               }
            }
         }
      }

      return this.order;
   }

   public @Nullable Class<?> getBeanType() {
      return this.beanType;
   }

   public Object resolveBean() {
      if (this.resolvedBean == null) {
         Object resolvedBean = this.beanFactory.getBean(this.beanName);
         if (!this.isSingleton) {
            return resolvedBean;
         }

         this.resolvedBean = resolvedBean;
      }

      return this.resolvedBean;
   }

   public boolean isApplicableToBeanType(@Nullable Class<?> beanType) {
      return this.beanTypePredicate.test(beanType);
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof ControllerAdviceBean that && this.beanName.equals(that.beanName) && this.beanFactory == that.beanFactory;
   }

   @Override
   public int hashCode() {
      return this.beanName.hashCode();
   }

   @Override
   public String toString() {
      return this.beanName;
   }

   public static List<ControllerAdviceBean> findAnnotatedBeans(ApplicationContext context) {
      return findAnnotatedBeans(context);
   }

   public static List<ControllerAdviceBean> findAnnotatedBeans(ListableBeanFactory beanFactory) {
      if (beanFactory instanceof ConfigurableApplicationContext cac) {
         beanFactory = cac.getBeanFactory();
      }

      List<ControllerAdviceBean> adviceBeans = new ArrayList<>();

      for (String name : BeanFactoryUtils.beanNamesForTypeIncludingAncestors(beanFactory, Object.class)) {
         if (!ScopedProxyUtils.isScopedTarget(name)) {
            ControllerAdvice controllerAdvice = (ControllerAdvice)beanFactory.findAnnotationOnBean(name, ControllerAdvice.class);
            if (controllerAdvice != null) {
               adviceBeans.add(new ControllerAdviceBean(name, beanFactory, controllerAdvice));
            }
         }
      }

      OrderComparator.sort(adviceBeans);
      return adviceBeans;
   }

   private static @Nullable Class<?> getBeanType(String beanName, BeanFactory beanFactory) {
      Class<?> beanType = beanFactory.getType(beanName);
      return beanType != null ? ClassUtils.getUserClass(beanType) : null;
   }

   private static HandlerTypePredicate createBeanTypePredicate(ControllerAdvice controllerAdvice) {
      return HandlerTypePredicate.builder()
         .basePackage(controllerAdvice.basePackages())
         .basePackageClass(controllerAdvice.basePackageClasses())
         .assignableType(controllerAdvice.assignableTypes())
         .annotation(controllerAdvice.annotations())
         .build();
   }
}
