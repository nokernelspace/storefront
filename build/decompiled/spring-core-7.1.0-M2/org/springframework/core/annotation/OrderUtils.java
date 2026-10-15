package org.springframework.core.annotation;

import java.lang.reflect.AnnotatedElement;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ConcurrentReferenceHashMap;

public abstract class OrderUtils {
   private static final Object NOT_ANNOTATED = new Object();
   private static final String JAKARTA_PRIORITY_ANNOTATION = "jakarta.annotation.Priority";
   static final Map<AnnotatedElement, Object> orderCache = new ConcurrentReferenceHashMap<>(64);

   public static int getOrder(Class<?> type, int defaultOrder) {
      Integer order = getOrder(type);
      return order != null ? order : defaultOrder;
   }

   public static @Nullable Integer getOrder(Class<?> type, @Nullable Integer defaultOrder) {
      Integer order = getOrder(type);
      return order != null ? order : defaultOrder;
   }

   public static @Nullable Integer getOrder(Class<?> type) {
      return getOrder((AnnotatedElement)type);
   }

   public static @Nullable Integer getOrder(AnnotatedElement element) {
      return getOrderFromAnnotations(element, MergedAnnotations.from(element, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY));
   }

   static @Nullable Integer getOrderFromAnnotations(AnnotatedElement element, MergedAnnotations annotations) {
      if (!(element instanceof Class)) {
         return findOrder(annotations);
      }

      Object cached = orderCache.get(element);
      if (cached != null) {
         return cached instanceof Integer integer ? integer : null;
      }

      Integer result = findOrder(annotations);
      orderCache.put(element, result != null ? result : NOT_ANNOTATED);
      return result;
   }

   private static @Nullable Integer findOrder(MergedAnnotations annotations) {
      MergedAnnotation<Order> orderAnnotation = annotations.get(Order.class);
      if (orderAnnotation.isPresent()) {
         return orderAnnotation.getInt("value");
      }

      MergedAnnotation<?> priorityAnnotation = annotations.get("jakarta.annotation.Priority");
      return priorityAnnotation.isPresent() ? priorityAnnotation.getInt("value") : null;
   }

   public static @Nullable Integer getPriority(Class<?> type) {
      return MergedAnnotations.from(type, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY)
         .get("jakarta.annotation.Priority")
         .getValue("value", Integer.class)
         .orElse(null);
   }
}
