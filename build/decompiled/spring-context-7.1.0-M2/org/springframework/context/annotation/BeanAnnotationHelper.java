package org.springframework.context.annotation;

import java.lang.reflect.Method;
import java.util.Map;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotationAttributes;
import org.springframework.core.type.MethodMetadata;
import org.springframework.util.ConcurrentReferenceHashMap;

abstract class BeanAnnotationHelper {
   private static final Map<Method, String> beanNameCache = new ConcurrentReferenceHashMap();
   private static final Map<Method, Boolean> scopedProxyCache = new ConcurrentReferenceHashMap();

   public static boolean isBeanAnnotated(Method method) {
      return AnnotatedElementUtils.hasAnnotation(method, Bean.class);
   }

   public static String determineBeanNameFor(Method beanMethod, ConfigurableBeanFactory beanFactory) {
      String beanName = retrieveBeanNameFor(beanMethod);
      return beanFactory.getSingleton("org.springframework.context.annotation.internalConfigurationBeanNameGenerator") instanceof ConfigurationBeanNameGenerator cbng
         ? cbng.deriveBeanName(MethodMetadata.introspect(beanMethod), !beanName.isEmpty() ? beanName : null)
         : determineBeanNameFrom(beanName, beanMethod);
   }

   public static String determineBeanNameFor(Method beanMethod) {
      return determineBeanNameFrom(retrieveBeanNameFor(beanMethod), beanMethod);
   }

   private static String retrieveBeanNameFor(Method beanMethod) {
      String beanName = beanNameCache.get(beanMethod);
      if (beanName == null) {
         beanName = "";
         AnnotationAttributes bean = AnnotatedElementUtils.findMergedAnnotationAttributes(beanMethod, Bean.class, false, false);
         if (bean != null) {
            String[] names = bean.getStringArray("name");
            if (names.length > 0) {
               beanName = names[0];
            }
         }

         beanNameCache.put(beanMethod, beanName);
      }

      return beanName;
   }

   private static String determineBeanNameFrom(String derivedBeanName, Method beanMethod) {
      return !derivedBeanName.isEmpty() ? derivedBeanName : beanMethod.getName();
   }

   public static boolean isScopedProxy(Method beanMethod) {
      Boolean scopedProxy = scopedProxyCache.get(beanMethod);
      if (scopedProxy == null) {
         AnnotationAttributes scope = AnnotatedElementUtils.findMergedAnnotationAttributes(beanMethod, Scope.class, false, false);
         scopedProxy = scope != null && scope.getEnum("proxyMode") != ScopedProxyMode.NO;
         scopedProxyCache.put(beanMethod, scopedProxy);
      }

      return scopedProxy;
   }

   static void clearCaches() {
      scopedProxyCache.clear();
      beanNameCache.clear();
   }
}
