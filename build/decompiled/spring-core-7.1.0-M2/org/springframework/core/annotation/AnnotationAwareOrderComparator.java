package org.springframework.core.annotation;

import java.lang.reflect.AnnotatedElement;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.core.DecoratingProxy;
import org.springframework.core.OrderComparator;

public class AnnotationAwareOrderComparator extends OrderComparator {
   public static final AnnotationAwareOrderComparator INSTANCE = new AnnotationAwareOrderComparator();

   @Override
   protected @Nullable Integer findOrder(Object obj) {
      Integer order = super.findOrder(obj);
      return order != null ? order : this.findOrderFromAnnotation(obj);
   }

   private @Nullable Integer findOrderFromAnnotation(Object obj) {
      AnnotatedElement element = obj instanceof AnnotatedElement ae ? ae : obj.getClass();
      MergedAnnotations annotations = MergedAnnotations.from(element, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY);
      Integer order = OrderUtils.getOrderFromAnnotations(element, annotations);
      return order == null && obj instanceof DecoratingProxy decoratingProxy ? this.findOrderFromAnnotation(decoratingProxy.getDecoratedClass()) : order;
   }

   @Override
   public @Nullable Integer getPriority(Object obj) {
      if (obj instanceof Class<?> clazz) {
         return OrderUtils.getPriority(clazz);
      } else {
         Integer priority = OrderUtils.getPriority(obj.getClass());
         return priority == null && obj instanceof DecoratingProxy decoratingProxy ? this.getPriority(decoratingProxy.getDecoratedClass()) : priority;
      }
   }

   public static void sort(List<?> list) {
      if (list.size() > 1) {
         list.sort(INSTANCE);
      }
   }

   public static void sort(Object[] array) {
      if (array.length > 1) {
         Arrays.sort(array, INSTANCE);
      }
   }

   public static void sortIfNecessary(Object value) {
      if (value instanceof Object[] objects) {
         sort(objects);
      } else if (value instanceof List<?> list) {
         sort(list);
      }
   }
}
