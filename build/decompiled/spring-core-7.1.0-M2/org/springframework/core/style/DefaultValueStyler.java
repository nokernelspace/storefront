package org.springframework.core.style;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.Map.Entry;
import org.jspecify.annotations.Nullable;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;

public class DefaultValueStyler implements ValueStyler {
   private static final String EMPTY = "[[empty]]";
   private static final String NULL = "[null]";
   private static final String COLLECTION = "collection";
   private static final String SET = "set";
   private static final String LIST = "list";
   private static final String MAP = "map";
   private static final String EMPTY_MAP = "map[[empty]]";
   private static final String ARRAY = "array";

   @Override
   public String style(@Nullable Object value) {
      if (value == null) {
         return this.styleNull();
      } else if (value instanceof String str) {
         return this.styleString(str);
      } else if (value instanceof Class<?> clazz) {
         return this.styleClass(clazz);
      } else if (value instanceof Method method) {
         return this.styleMethod(method);
      } else if (value instanceof Map<?, ?> map) {
         return this.styleMap(map);
      } else if (value instanceof Entry<?, ?> entry) {
         return this.styleMapEntry(entry);
      } else if (value instanceof Collection<?> collection) {
         return this.styleCollection(collection);
      } else {
         return value.getClass().isArray() ? this.styleArray(ObjectUtils.toObjectArray(value)) : this.styleObject(value);
      }
   }

   protected String styleNull() {
      return "[null]";
   }

   protected String styleString(String str) {
      return "'" + str + "'";
   }

   protected String styleClass(Class<?> clazz) {
      return ClassUtils.getShortName(clazz);
   }

   protected String styleMethod(Method method) {
      return method.getName() + "@" + ClassUtils.getShortName(method.getDeclaringClass());
   }

   protected <K, V> String styleMap(Map<K, V> map) {
      if (map.isEmpty()) {
         return "map[[empty]]";
      }

      StringJoiner result = new StringJoiner(", ", "[", "]");

      for (Entry<K, V> entry : map.entrySet()) {
         result.add(this.styleMapEntry(entry));
      }

      return "map" + result;
   }

   protected String styleMapEntry(Entry<?, ?> entry) {
      return this.style(entry.getKey()) + " -> " + this.style(entry.getValue());
   }

   protected String styleCollection(Collection<?> collection) {
      String collectionType = getCollectionTypeString(collection);
      if (collection.isEmpty()) {
         return collectionType + "[[empty]]";
      }

      StringJoiner result = new StringJoiner(", ", "[", "]");

      for (Object element : collection) {
         result.add(this.style(element));
      }

      return collectionType + result;
   }

   protected String styleArray(Object[] array) {
      if (array.length == 0) {
         return "array<" + ClassUtils.getShortName(array.getClass().componentType()) + ">[[empty]]";
      }

      StringJoiner result = new StringJoiner(", ", "[", "]");

      for (Object element : array) {
         result.add(this.style(element));
      }

      return "array<" + ClassUtils.getShortName(array.getClass().componentType()) + ">" + result;
   }

   protected String styleObject(Object obj) {
      return String.valueOf(obj);
   }

   private static String getCollectionTypeString(Collection<?> collection) {
      if (collection instanceof List) {
         return "list";
      } else {
         return collection instanceof Set ? "set" : "collection";
      }
   }
}
