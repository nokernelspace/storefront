package org.springframework.beans.factory;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.core.ResolvableType;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public abstract class BeanFactoryUtils {
   public static final String GENERATED_BEAN_NAME_SEPARATOR = "#";
   private static final Map<String, String> transformedBeanNameCache = new ConcurrentHashMap<>();

   public static boolean isFactoryDereference(@Nullable String name) {
      return name != null && !name.isEmpty() && name.charAt(0) == '&';
   }

   public static String transformedBeanName(String name) {
      Assert.notNull(name, "'name' must not be null");
      return !name.isEmpty() && name.charAt(0) == '&' ? transformedBeanNameCache.computeIfAbsent(name, beanName -> {
         do {
            beanName = beanName.substring(1);
         } while (beanName.charAt(0) == '&');

         return (String)beanName;
      }) : name;
   }

   public static boolean isGeneratedBeanName(@Nullable String name) {
      return name != null && name.contains("#");
   }

   public static String originalBeanName(String name) {
      Assert.notNull(name, "'name' must not be null");
      int separatorIndex = name.indexOf("#");
      return separatorIndex != -1 ? name.substring(0, separatorIndex) : name;
   }

   public static int countBeansIncludingAncestors(ListableBeanFactory lbf) {
      return beanNamesIncludingAncestors(lbf).length;
   }

   public static String[] beanNamesIncludingAncestors(ListableBeanFactory lbf) {
      return beanNamesForTypeIncludingAncestors(lbf, Object.class);
   }

   public static String[] beanNamesForTypeIncludingAncestors(ListableBeanFactory lbf, ResolvableType type) {
      Assert.notNull(lbf, "ListableBeanFactory must not be null");
      String[] result = lbf.getBeanNamesForType(type);
      if (lbf instanceof HierarchicalBeanFactory hbf && hbf.getParentBeanFactory() instanceof ListableBeanFactory pbf) {
         String[] parentResult = beanNamesForTypeIncludingAncestors(pbf, type);
         result = mergeNamesWithParent(result, parentResult, hbf);
      }

      return result;
   }

   public static String[] beanNamesForTypeIncludingAncestors(ListableBeanFactory lbf, ResolvableType type, boolean includeNonSingletons, boolean allowEagerInit) {
      Assert.notNull(lbf, "ListableBeanFactory must not be null");
      String[] result = lbf.getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
      if (lbf instanceof HierarchicalBeanFactory hbf && hbf.getParentBeanFactory() instanceof ListableBeanFactory pbf) {
         String[] parentResult = beanNamesForTypeIncludingAncestors(pbf, type, includeNonSingletons, allowEagerInit);
         result = mergeNamesWithParent(result, parentResult, hbf);
      }

      return result;
   }

   public static String[] beanNamesForTypeIncludingAncestors(ListableBeanFactory lbf, Class<?> type) {
      Assert.notNull(lbf, "ListableBeanFactory must not be null");
      String[] result = lbf.getBeanNamesForType(type);
      if (lbf instanceof HierarchicalBeanFactory hbf && hbf.getParentBeanFactory() instanceof ListableBeanFactory pbf) {
         String[] parentResult = beanNamesForTypeIncludingAncestors(pbf, type);
         result = mergeNamesWithParent(result, parentResult, hbf);
      }

      return result;
   }

   public static String[] beanNamesForTypeIncludingAncestors(ListableBeanFactory lbf, Class<?> type, boolean includeNonSingletons, boolean allowEagerInit) {
      Assert.notNull(lbf, "ListableBeanFactory must not be null");
      String[] result = lbf.getBeanNamesForType(type, includeNonSingletons, allowEagerInit);
      if (lbf instanceof HierarchicalBeanFactory hbf && hbf.getParentBeanFactory() instanceof ListableBeanFactory pbf) {
         String[] parentResult = beanNamesForTypeIncludingAncestors(pbf, type, includeNonSingletons, allowEagerInit);
         result = mergeNamesWithParent(result, parentResult, hbf);
      }

      return result;
   }

   public static String[] beanNamesForAnnotationIncludingAncestors(ListableBeanFactory lbf, Class<? extends Annotation> annotationType) {
      Assert.notNull(lbf, "ListableBeanFactory must not be null");
      String[] result = lbf.getBeanNamesForAnnotation(annotationType);
      if (lbf instanceof HierarchicalBeanFactory hbf && hbf.getParentBeanFactory() instanceof ListableBeanFactory pbf) {
         String[] parentResult = beanNamesForAnnotationIncludingAncestors(pbf, annotationType);
         result = mergeNamesWithParent(result, parentResult, hbf);
      }

      return result;
   }

   public static <T> Map<String, T> beansOfTypeIncludingAncestors(ListableBeanFactory lbf, Class<T> type) throws BeansException {
      Assert.notNull(lbf, "ListableBeanFactory must not be null");
      Map<String, T> result = new LinkedHashMap<>(4);
      result.putAll(lbf.getBeansOfType(type));
      if (lbf instanceof HierarchicalBeanFactory hbf && hbf.getParentBeanFactory() instanceof ListableBeanFactory pbf) {
         Map<String, T> parentResult = beansOfTypeIncludingAncestors(pbf, type);
         parentResult.forEach((beanName, beanInstance) -> {
            if (!result.containsKey(beanName) && !hbf.containsLocalBean(beanName)) {
               result.put(beanName, (T)beanInstance);
            }
         });
      }

      return result;
   }

   public static <T> Map<String, T> beansOfTypeIncludingAncestors(ListableBeanFactory lbf, Class<T> type, boolean includeNonSingletons, boolean allowEagerInit) throws BeansException {
      Assert.notNull(lbf, "ListableBeanFactory must not be null");
      Map<String, T> result = new LinkedHashMap<>(4);
      result.putAll(lbf.getBeansOfType(type, includeNonSingletons, allowEagerInit));
      if (lbf instanceof HierarchicalBeanFactory hbf && hbf.getParentBeanFactory() instanceof ListableBeanFactory pbf) {
         Map<String, T> parentResult = beansOfTypeIncludingAncestors(pbf, type, includeNonSingletons, allowEagerInit);
         parentResult.forEach((beanName, beanInstance) -> {
            if (!result.containsKey(beanName) && !hbf.containsLocalBean(beanName)) {
               result.put(beanName, (T)beanInstance);
            }
         });
      }

      return result;
   }

   public static <T> T beanOfTypeIncludingAncestors(ListableBeanFactory lbf, Class<T> type) throws BeansException {
      Map<String, T> beansOfType = beansOfTypeIncludingAncestors(lbf, type);
      return uniqueBean(type, beansOfType);
   }

   public static <T> T beanOfTypeIncludingAncestors(ListableBeanFactory lbf, Class<T> type, boolean includeNonSingletons, boolean allowEagerInit) throws BeansException {
      Map<String, T> beansOfType = beansOfTypeIncludingAncestors(lbf, type, includeNonSingletons, allowEagerInit);
      return uniqueBean(type, beansOfType);
   }

   public static <T> T beanOfType(ListableBeanFactory lbf, Class<T> type) throws BeansException {
      Assert.notNull(lbf, "ListableBeanFactory must not be null");
      Map<String, T> beansOfType = lbf.getBeansOfType(type);
      return uniqueBean(type, beansOfType);
   }

   public static <T> T beanOfType(ListableBeanFactory lbf, Class<T> type, boolean includeNonSingletons, boolean allowEagerInit) throws BeansException {
      Assert.notNull(lbf, "ListableBeanFactory must not be null");
      Map<String, T> beansOfType = lbf.getBeansOfType(type, includeNonSingletons, allowEagerInit);
      return uniqueBean(type, beansOfType);
   }

   private static String[] mergeNamesWithParent(String[] result, String[] parentResult, HierarchicalBeanFactory hbf) {
      if (parentResult.length == 0) {
         return result;
      }

      List<String> merged = new ArrayList<>(result.length + parentResult.length);
      merged.addAll(Arrays.asList(result));

      for (String beanName : parentResult) {
         if (!merged.contains(beanName) && !hbf.containsLocalBean(beanName)) {
            merged.add(beanName);
         }
      }

      return StringUtils.toStringArray(merged);
   }

   private static <T> T uniqueBean(Class<T> type, Map<String, T> matchingBeans) {
      int count = matchingBeans.size();
      if (count == 1) {
         return matchingBeans.values().iterator().next();
      } else if (count > 1) {
         throw new NoUniqueBeanDefinitionException(type, matchingBeans.keySet());
      } else {
         throw new NoSuchBeanDefinitionException(type);
      }
   }
}
