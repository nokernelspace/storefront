package org.springframework.beans.factory.annotation;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.Assert;

public abstract class BeanFactoryAnnotationUtils {
   public static <T> Map<String, T> qualifiedBeansOfType(ListableBeanFactory beanFactory, Class<T> beanType, String qualifier) throws BeansException {
      String[] candidateBeans = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(beanFactory, beanType);
      Map<String, T> result = new LinkedHashMap<>(4);

      for (String beanName : candidateBeans) {
         if (isQualifierMatch(qualifier::equals, beanName, beanFactory)) {
            result.put(beanName, beanFactory.getBean(beanName, beanType));
         }
      }

      return result;
   }

   public static <T> T qualifiedBeanOfType(BeanFactory beanFactory, Class<T> beanType, String qualifier) throws BeansException {
      Assert.notNull(beanFactory, "BeanFactory must not be null");
      if (beanFactory instanceof ListableBeanFactory lbf) {
         return qualifiedBeanOfType(lbf, beanType, qualifier);
      } else if (beanFactory.containsBean(qualifier) && beanFactory.isTypeMatch(qualifier, beanType)) {
         return beanFactory.getBean(qualifier, beanType);
      } else {
         throw new NoSuchBeanDefinitionException(
            qualifier,
            "No matching "
               + beanType.getSimpleName()
               + " bean found for bean name '"
               + qualifier
               + "'! (Note: Qualifier matching not supported because given BeanFactory does not implement ConfigurableListableBeanFactory.)"
         );
      }
   }

   private static <T> T qualifiedBeanOfType(ListableBeanFactory beanFactory, Class<T> beanType, String qualifier) {
      String[] candidateBeans = BeanFactoryUtils.beanNamesForTypeIncludingAncestors(beanFactory, beanType);
      String matchingBean = null;

      for (String beanName : candidateBeans) {
         if (isQualifierMatch(qualifier::equals, beanName, beanFactory)) {
            if (matchingBean != null) {
               throw new NoUniqueBeanDefinitionException(beanType, matchingBean, beanName);
            }

            matchingBean = beanName;
         }
      }

      if (matchingBean != null) {
         return beanFactory.getBean(matchingBean, beanType);
      } else if (beanFactory.containsBean(qualifier) && beanFactory.isTypeMatch(qualifier, beanType)) {
         return beanFactory.getBean(qualifier, beanType);
      } else {
         throw new NoSuchBeanDefinitionException(
            qualifier,
            "No matching " + beanType.getSimpleName() + " bean found for qualifier '" + qualifier + "' - neither qualifier match nor bean name match!"
         );
      }
   }

   public static @Nullable String getQualifierValue(AnnotatedElement annotatedElement) {
      Qualifier qualifier = (Qualifier)AnnotationUtils.getAnnotation(annotatedElement, Qualifier.class);
      return qualifier != null ? qualifier.value() : null;
   }

   public static boolean isQualifierMatch(Predicate<String> qualifier, String beanName, @Nullable BeanFactory beanFactory) {
      if (qualifier.test(beanName)) {
         return true;
      }

      if (beanFactory != null) {
         for (String alias : beanFactory.getAliases(beanName)) {
            if (qualifier.test(alias)) {
               return true;
            }
         }

         try {
            Class<?> beanType = beanFactory.getType(beanName);
            if (beanFactory instanceof ConfigurableBeanFactory cbf) {
               BeanDefinition bd = cbf.getMergedBeanDefinition(beanName);
               if (bd instanceof AbstractBeanDefinition abd) {
                  AutowireCandidateQualifier candidate = abd.getQualifier(Qualifier.class.getName());
                  if (candidate != null) {
                     Object value = candidate.getAttribute("value");
                     if (value != null && qualifier.test(value.toString())) {
                        return true;
                     }
                  }
               }

               if (bd instanceof RootBeanDefinition rbd) {
                  Method factoryMethod = rbd.getResolvedFactoryMethod();
                  if (factoryMethod != null) {
                     Qualifier targetAnnotation = (Qualifier)AnnotationUtils.getAnnotation(factoryMethod, Qualifier.class);
                     if (targetAnnotation != null) {
                        return qualifier.test(targetAnnotation.value());
                     }
                  }
               }
            }

            if (beanType != null) {
               Qualifier targetAnnotation = (Qualifier)AnnotationUtils.getAnnotation(beanType, Qualifier.class);
               if (targetAnnotation != null) {
                  return qualifier.test(targetAnnotation.value());
               }
            }
         } catch (NoSuchBeanDefinitionException var9) {
         }
      }

      return false;
   }
}
