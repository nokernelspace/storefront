package org.springframework.beans.factory.support;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.BeanFactoryUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

public class SimpleAutowireCandidateResolver implements AutowireCandidateResolver {
   public static final SimpleAutowireCandidateResolver INSTANCE = new SimpleAutowireCandidateResolver();

   @Override
   public AutowireCandidateResolver cloneIfNecessary() {
      return this;
   }

   public static <T> Map<String, T> resolveAutowireCandidates(ConfigurableListableBeanFactory lbf, Class<T> type) {
      return resolveAutowireCandidates(lbf, type, true, true);
   }

   public static <T> Map<String, T> resolveAutowireCandidates(
      ConfigurableListableBeanFactory lbf, Class<T> type, boolean includeNonSingletons, boolean allowEagerInit
   ) {
      Map<String, T> candidates = new LinkedHashMap<>();

      for (String beanName : BeanFactoryUtils.beanNamesForTypeIncludingAncestors(lbf, type, includeNonSingletons, allowEagerInit)) {
         if (AutowireUtils.isAutowireCandidate(lbf, beanName)) {
            Object beanInstance = lbf.getBean(beanName);
            if (!(beanInstance instanceof NullBean)) {
               candidates.put(beanName, (T)beanInstance);
            }
         }
      }

      return candidates;
   }
}
