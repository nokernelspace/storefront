package org.springframework.beans.factory.annotation;

import java.lang.annotation.Annotation;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanClassLoaderAware;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.Ordered;
import org.springframework.util.ClassUtils;

public class CustomAutowireConfigurer implements BeanFactoryPostProcessor, BeanClassLoaderAware, Ordered {
   private int order = Integer.MAX_VALUE;
   private @Nullable Set<?> customQualifierTypes;
   private @Nullable ClassLoader beanClassLoader = ClassUtils.getDefaultClassLoader();

   public void setOrder(int order) {
      this.order = order;
   }

   public int getOrder() {
      return this.order;
   }

   @Override
   public void setBeanClassLoader(@Nullable ClassLoader beanClassLoader) {
      this.beanClassLoader = beanClassLoader;
   }

   public void setCustomQualifierTypes(Set<?> customQualifierTypes) {
      this.customQualifierTypes = customQualifierTypes;
   }

   @Override
   public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
      if (this.customQualifierTypes != null) {
         if (!(beanFactory instanceof DefaultListableBeanFactory dlbf)) {
            throw new IllegalStateException("CustomAutowireConfigurer needs to operate on a DefaultListableBeanFactory");
         }

         if (!(dlbf.getAutowireCandidateResolver() instanceof QualifierAnnotationAutowireCandidateResolver)) {
            dlbf.setAutowireCandidateResolver(new QualifierAnnotationAutowireCandidateResolver());
         }

         QualifierAnnotationAutowireCandidateResolver resolver = (QualifierAnnotationAutowireCandidateResolver)dlbf.getAutowireCandidateResolver();

         for (Object value : this.customQualifierTypes) {
            Class<? extends Annotation> customType = null;
            if (!(value instanceof Class<? extends Annotation> var8)) {
               if (!(value instanceof String className)) {
                  throw new IllegalArgumentException("Invalid value [" + value + "] for custom qualifier type: needs to be Class or String.");
               }

               var8 = ClassUtils.resolveClassName(className, this.beanClassLoader);
            }

            if (!Annotation.class.isAssignableFrom(var8)) {
               throw new IllegalArgumentException("Qualifier type [" + var8.getName() + "] needs to be annotation type");
            }

            resolver.addQualifierType(var8);
         }
      }
   }
}
