package org.springframework.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.lang.Contract;

public abstract class CollectionUtils {
   static final float DEFAULT_LOAD_FACTOR = 0.75F;

   @Contract("null -> true")
   public static boolean isEmpty(@Nullable Collection<? extends @Nullable Object> collection) {
      return collection == null || collection.isEmpty();
   }

   @Contract("null -> true")
   public static boolean isEmpty(@Nullable Map<?, ? extends @Nullable Object> map) {
      return map == null || map.isEmpty();
   }

   public static <K, V> HashMap<K, V> newHashMap(int expectedSize) {
      return new HashMap<>(computeInitialCapacity(expectedSize), 0.75F);
   }

   public static <K, V> LinkedHashMap<K, V> newLinkedHashMap(int expectedSize) {
      return new LinkedHashMap<>(computeInitialCapacity(expectedSize), 0.75F);
   }

   public static <E> HashSet<E> newHashSet(int expectedSize) {
      return new HashSet<>(computeInitialCapacity(expectedSize), 0.75F);
   }

   public static <E> LinkedHashSet<E> newLinkedHashSet(int expectedSize) {
      return new LinkedHashSet<>(computeInitialCapacity(expectedSize), 0.75F);
   }

   private static int computeInitialCapacity(int expectedSize) {
      return (int)Math.ceil(expectedSize / 0.75);
   }

   public static List<?> arrayToList(@Nullable Object source) {
      return Arrays.asList(ObjectUtils.toObjectArray(source));
   }

   public static <E> void mergeArrayIntoCollection(@Nullable Object array, Collection<E> collection) {
      Object[] arr = ObjectUtils.toObjectArray(array);
      Collections.addAll(collection, (E[])arr);
   }

   public static <K, V> void mergePropertiesIntoMap(@Nullable Properties props, Map<K, V> map) {
      if (props != null) {
         Enumeration<?> en = props.propertyNames();

         while (en.hasMoreElements()) {
            String key = (String)en.nextElement();
            Object value = props.get(key);
            if (value == null) {
               value = props.getProperty(key);
            }

            map.put((K)key, (V)value);
         }
      }
   }

   @Contract("null, _ -> false")
   public static boolean contains(@Nullable Iterator<? extends @Nullable Object> iterator, @Nullable Object element) {
      if (iterator != null) {
         while (iterator.hasNext()) {
            Object candidate = iterator.next();
            if (ObjectUtils.nullSafeEquals(candidate, element)) {
               return true;
            }
         }
      }

      return false;
   }

   @Contract("null, _ -> false")
   public static boolean contains(@Nullable Enumeration<? extends @Nullable Object> enumeration, @Nullable Object element) {
      if (enumeration != null) {
         while (enumeration.hasMoreElements()) {
            Object candidate = enumeration.nextElement();
            if (ObjectUtils.nullSafeEquals(candidate, element)) {
               return true;
            }
         }
      }

      return false;
   }

   @Contract("null, _ -> false")
   public static boolean containsInstance(@Nullable Collection<? extends @Nullable Object> collection, @Nullable Object element) {
      if (collection != null) {
         for (Object candidate : collection) {
            if (candidate == element) {
               return true;
            }
         }
      }

      return false;
   }

   public static boolean containsAny(Collection<? extends @Nullable Object> source, Collection<? extends @Nullable Object> candidates) {
      if (!isEmpty(source) && !isEmpty(candidates)) {
         for (Object candidate : candidates) {
            if (source.contains(candidate)) {
               return true;
            }
         }

         return false;
      } else {
         return false;
      }
   }

   public static <E> @Nullable E findFirstMatch(Collection<?> source, Collection<E> candidates) {
      if (!isEmpty((Collection<? extends Object>)source) && !isEmpty(candidates)) {
         for (E candidate : candidates) {
            if (source.contains(candidate)) {
               return candidate;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   @Contract("null, _ -> null")
   public static <T> @Nullable T findValueOfType(@Nullable Collection<?> collection, @Nullable Class<T> type) {
      if (isEmpty((Collection<? extends Object>)collection)) {
         return null;
      }

      T value = null;

      for (Object element : collection) {
         if (type == null || type.isInstance(element)) {
            if (value != null) {
               return null;
            }

            value = (T)element;
         }
      }

      return value;
   }

   public static @Nullable Object findValueOfType(Collection<?> collection, Class<?>[] types) {
      if (!isEmpty((Collection<? extends Object>)collection) && !ObjectUtils.isEmpty(types)) {
         for (Class<?> type : types) {
            Object value = findValueOfType(collection, type);
            if (value != null) {
               return value;
            }
         }

         return null;
      } else {
         return null;
      }
   }

   public static boolean hasUniqueObject(Collection<?> collection) {
      if (isEmpty((Collection<? extends Object>)collection)) {
         return false;
      }

      boolean hasCandidate = false;
      Object candidate = null;

      for (Object elem : collection) {
         if (!hasCandidate) {
            hasCandidate = true;
            candidate = elem;
         } else if (candidate != elem) {
            return false;
         }
      }

      return true;
   }

   public static @Nullable Class<?> findCommonElementType(Collection<?> collection) {
      if (isEmpty((Collection<? extends Object>)collection)) {
         return null;
      }

      Class<?> candidate = null;

      for (Object val : collection) {
         if (val != null) {
            if (candidate == null) {
               candidate = val.getClass();
            } else if (candidate != val.getClass()) {
               return null;
            }
         }
      }

      return candidate;
   }

   @Contract("null -> null")
   public static <T> @Nullable T firstElement(@Nullable Set<T> set) {
      if (isEmpty(set)) {
         return null;
      } else if (set instanceof SortedSet<T> sortedSet) {
         return sortedSet.first();
      } else {
         Iterator<T> it = set.iterator();
         T first = null;
         if (it.hasNext()) {
            first = it.next();
         }

         return first;
      }
   }

   @Contract("null -> null")
   public static <T> @Nullable T firstElement(@Nullable List<T> list) {
      return isEmpty(list) ? null : list.get(0);
   }

   @Contract("null -> null")
   public static <T> @Nullable T lastElement(@Nullable Set<T> set) {
      if (isEmpty(set)) {
         return null;
      } else if (set instanceof SortedSet<T> sortedSet) {
         return sortedSet.last();
      } else {
         Iterator<T> it = set.iterator();
         T last = null;

         while (it.hasNext()) {
            last = it.next();
         }

         return last;
      }
   }

   @Contract("null -> null")
   public static <T> @Nullable T lastElement(@Nullable List<T> list) {
      return isEmpty(list) ? null : list.get(list.size() - 1);
   }

   public static <A, E extends A> A[] toArray(Enumeration<E> enumeration, A[] array) {
      ArrayList<A> elements = new ArrayList();

      while (enumeration.hasMoreElements()) {
         elements.add((A)enumeration.nextElement());
      }

      return elements.toArray(array);
   }

   public static <E> Iterator<E> toIterator(@Nullable Enumeration<E> enumeration) {
      return enumeration != null ? enumeration.asIterator() : Collections.emptyIterator();
   }

   public static <K, V> MultiValueMap<K, V> toMultiValueMap(Map<K, List<V>> targetMap) {
      return new MultiValueMapAdapter<>(targetMap);
   }

   public static <K, V> MultiValueMap<K, V> unmodifiableMultiValueMap(MultiValueMap<? extends K, ? extends V> targetMap) {
      Assert.notNull(targetMap, "'targetMap' must not be null");
      return targetMap instanceof UnmodifiableMultiValueMap ? targetMap : new UnmodifiableMultiValueMap<>(targetMap);
   }

   public static <K, V> Map<K, V> compositeMap(Map<K, V> first, Map<K, V> second) {
      return new CompositeMap<>(first, second);
   }

   public static <K, V> Map<K, V> compositeMap(
      Map<K, V> first, Map<K, V> second, @Nullable BiFunction<K, V, V> putFunction, @Nullable Consumer<Map<K, V>> putAllFunction
   ) {
      return new CompositeMap<>(first, second, putFunction, putAllFunction);
   }
}
