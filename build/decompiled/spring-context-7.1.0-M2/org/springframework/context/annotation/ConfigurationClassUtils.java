package org.springframework.context.annotation;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.aop.framework.AopInfrastructureBean;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.cglib.proxy.Enhancer;
import org.springframework.context.event.EventListenerFactory;
import org.springframework.core.Conventions;
import org.springframework.core.annotation.Order;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.stereotype.Component;

public abstract class ConfigurationClassUtils {
   static final String CONFIGURATION_CLASS_FULL = "full";
   static final String CONFIGURATION_CLASS_LITE = "lite";
   static final String CANDIDATE_ATTRIBUTE = Conventions.getQualifiedAttributeName(ConfigurationClassPostProcessor.class, "candidate");
   static final String CONFIGURATION_CLASS_ATTRIBUTE = Conventions.getQualifiedAttributeName(ConfigurationClassPostProcessor.class, "configurationClass");
   static final String ORDER_ATTRIBUTE = Conventions.getQualifiedAttributeName(ConfigurationClassPostProcessor.class, "order");
   private static final Log logger = LogFactory.getLog(ConfigurationClassUtils.class);
   private static final Set<String> candidateIndicators = Set.of(
      Component.class.getName(), ComponentScan.class.getName(), Import.class.getName(), ImportResource.class.getName()
   );

   public static Class<?> initializeConfigurationClass(Class<?> userClass) {
      Class<?> configurationClass = new ConfigurationClassEnhancer().enhance(userClass, null);
      Enhancer.registerStaticCallbacks(configurationClass, ConfigurationClassEnhancer.CALLBACKS);
      return configurationClass;
   }

   static boolean checkConfigurationClassCandidate(BeanDefinition beanDef, MetadataReaderFactory metadataReaderFactory) {
      String className = beanDef.getBeanClassName();
      if (className != null && beanDef.getFactoryMethodName() == null) {
         AnnotationMetadata metadata;
         if (beanDef instanceof AnnotatedBeanDefinition annotatedBd && className.equals(annotatedBd.getMetadata().getClassName())) {
            metadata = annotatedBd.getMetadata();
         } else if (beanDef instanceof AbstractBeanDefinition abstractBd && abstractBd.hasBeanClass()) {
            Class<?> beanClass = abstractBd.getBeanClass();
            if (BeanFactoryPostProcessor.class.isAssignableFrom(beanClass)
               || BeanPostProcessor.class.isAssignableFrom(beanClass)
               || AopInfrastructureBean.class.isAssignableFrom(beanClass)
               || EventListenerFactory.class.isAssignableFrom(beanClass)) {
               return false;
            }

            metadata = AnnotationMetadata.introspect(beanClass);
         } else {
            try {
               MetadataReader metadataReader = metadataReaderFactory.getMetadataReader(className);
               metadata = metadataReader.getAnnotationMetadata();
            } catch (IOException ex) {
               if (logger.isDebugEnabled()) {
                  logger.debug("Could not find class file for introspecting configuration annotations: " + className, ex);
               }

               return false;
            }
         }

         Map<String, Object> config = metadata.getAnnotationAttributes(Configuration.class.getName());
         if (config != null && !Boolean.FALSE.equals(config.get("proxyBeanMethods"))) {
            beanDef.setAttribute(CONFIGURATION_CLASS_ATTRIBUTE, "full");
         } else {
            if (config == null && !Boolean.TRUE.equals(beanDef.getAttribute(CANDIDATE_ATTRIBUTE)) && !isConfigurationCandidate(metadata)) {
               return false;
            }

            beanDef.setAttribute(CONFIGURATION_CLASS_ATTRIBUTE, "lite");
         }

         Integer order = getOrder(metadata);
         if (order != null) {
            beanDef.setAttribute(ORDER_ATTRIBUTE, order);
         }

         return true;
      } else {
         return false;
      }
   }

   static boolean isConfigurationCandidate(AnnotationMetadata metadata) {
      if (metadata.isInterface()) {
         return false;
      }

      for (String indicator : candidateIndicators) {
         if (metadata.isAnnotated(indicator)) {
            return true;
         }
      }

      return hasBeanMethods(metadata);
   }

   static boolean hasBeanMethods(AnnotationMetadata metadata) {
      try {
         return metadata.hasAnnotatedMethods(Bean.class.getName());
      } catch (Throwable ex) {
         if (logger.isDebugEnabled()) {
            logger.debug("Failed to introspect @Bean methods on class [" + metadata.getClassName() + "]: " + ex);
         }

         return false;
      }
   }

   public static Integer getOrder(AnnotationMetadata metadata) {
      Map<String, Object> orderAttributes = metadata.getAnnotationAttributes(Order.class.getName());
      return orderAttributes != null ? (Integer)orderAttributes.get("value") : null;
   }

   public static int getOrder(BeanDefinition beanDef) {
      Integer order = (Integer)beanDef.getAttribute(ORDER_ATTRIBUTE);
      return order != null ? order : Integer.MAX_VALUE;
   }
}
