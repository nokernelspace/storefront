package org.springframework.beans.support;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.BeansException;
import org.springframework.util.StringUtils;

@Deprecated(since = "7.0.3", forRemoval = true)
public class PropertyComparator<T> implements Comparator<T> {
   protected final Log logger = LogFactory.getLog(this.getClass());
   private final SortDefinition sortDefinition;

   public PropertyComparator(SortDefinition sortDefinition) {
      this.sortDefinition = sortDefinition;
   }

   public PropertyComparator(String property, boolean ignoreCase, boolean ascending) {
      this.sortDefinition = new MutableSortDefinition(property, ignoreCase, ascending);
   }

   public final SortDefinition getSortDefinition() {
      return this.sortDefinition;
   }

   @Override
   public int compare(T o1, T o2) {
      Object v1 = this.getPropertyValue(o1);
      Object v2 = this.getPropertyValue(o2);
      if (this.sortDefinition.isIgnoreCase() && v1 instanceof String text1 && v2 instanceof String text2) {
         v1 = text1.toLowerCase(Locale.ROOT);
         v2 = text2.toLowerCase(Locale.ROOT);
      }

      int result;
      try {
         if (v1 != null) {
            result = v2 != null ? ((Comparable)v1).compareTo(v2) : -1;
         } else {
            result = v2 != null ? 1 : 0;
         }
      } catch (RuntimeException ex) {
         if (this.logger.isDebugEnabled()) {
            this.logger.debug("Could not sort objects [" + o1 + "] and [" + o2 + "]", ex);
         }

         return 0;
      }

      return this.sortDefinition.isAscending() ? result : -result;
   }

   private @Nullable Object getPropertyValue(Object obj) {
      try {
         BeanWrapperImpl beanWrapper = new BeanWrapperImpl(false);
         beanWrapper.setWrappedInstance(obj);
         return beanWrapper.getPropertyValue(this.sortDefinition.getProperty());
      } catch (BeansException ex) {
         this.logger.debug("PropertyComparator could not access property - treating as null for sorting", ex);
         return null;
      }
   }

   public static void sort(List<?> source, SortDefinition sortDefinition) throws BeansException {
      if (StringUtils.hasText(sortDefinition.getProperty())) {
         source.sort(new PropertyComparator(sortDefinition));
      }
   }

   public static void sort(Object[] source, SortDefinition sortDefinition) throws BeansException {
      if (StringUtils.hasText(sortDefinition.getProperty())) {
         Arrays.sort(source, new PropertyComparator<>(sortDefinition));
      }
   }
}
