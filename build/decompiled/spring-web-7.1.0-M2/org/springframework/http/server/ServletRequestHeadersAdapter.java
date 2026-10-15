package org.springframework.http.server;

import jakarta.servlet.http.HttpServletRequest;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedCaseInsensitiveMap;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

final class ServletRequestHeadersAdapter implements MultiValueMap<String, String> {
   private final HttpServletRequest request;

   private ServletRequestHeadersAdapter(HttpServletRequest request) {
      this.request = request;
   }

   public @Nullable String getFirst(String key) {
      return this.request.getHeader(key);
   }

   public void add(String key, @Nullable String value) {
      throw immutableRequestException();
   }

   public void addAll(String key, List<? extends String> values) {
      throw immutableRequestException();
   }

   public void addAll(MultiValueMap<String, String> map) {
      throw httpHeadersMapException();
   }

   public void set(String key, @Nullable String value) {
      throw immutableRequestException();
   }

   public void setAll(Map<String, String> map) {
      throw immutableRequestException();
   }

   public Map<String, String> toSingleValueMap() {
      Map<String, String> map = new LinkedHashMap<>();
      Enumeration<String> names = this.request.getHeaderNames();

      while (names.hasMoreElements()) {
         String name = names.nextElement();
         map.put(name, this.request.getHeader(name));
      }

      return map;
   }

   public int size() {
      return this.keySet().size();
   }

   public boolean isEmpty() {
      return !this.request.getHeaderNames().hasMoreElements();
   }

   public boolean containsKey(Object key) {
      if (key instanceof String headerName) {
         Enumeration<String> names = this.request.getHeaderNames();

         while (names.hasMoreElements()) {
            if (headerName.equalsIgnoreCase(names.nextElement())) {
               return true;
            }
         }
      }

      return false;
   }

   public boolean containsValue(Object rawValue) {
      throw httpHeadersMapException();
   }

   public @Nullable List<String> get(Object key) {
      if (key instanceof String headerName) {
         Enumeration<String> values = this.request.getHeaders(headerName);
         if (values.hasMoreElements()) {
            String value = values.nextElement();
            if (!values.hasMoreElements()) {
               return Collections.singletonList(value);
            }

            List<String> result = new ArrayList<>(4);
            result.add(value);

            while (values.hasMoreElements()) {
               result.add(values.nextElement());
            }

            return result;
         }
      }

      return null;
   }

   public @Nullable List<String> put(String key, List<String> value) {
      throw immutableRequestException();
   }

   public @Nullable List<String> remove(Object key) {
      throw immutableRequestException();
   }

   public void putAll(Map<? extends String, ? extends List<String>> map) {
      throw httpHeadersMapException();
   }

   public void clear() {
      throw immutableRequestException();
   }

   public Set<String> keySet() {
      return new ServletRequestHeadersAdapter.HeaderNames();
   }

   public Collection<List<String>> values() {
      throw httpHeadersMapException();
   }

   public Set<Entry<String, List<String>>> entrySet() {
      throw httpHeadersMapException();
   }

   private static UnsupportedOperationException immutableRequestException() {
      return new UnsupportedOperationException("Request headers are immutable");
   }

   private static UnsupportedOperationException httpHeadersMapException() {
      return new UnsupportedOperationException("HttpHeaders does not support all Map operations");
   }

   @Override
   public String toString() {
      return HttpHeaders.formatHeaders(this);
   }

   static MultiValueMap<String, String> create(HttpServletRequest request) {
      return new ServletRequestHeadersAdapter.RequestHeaderOverrideWrapper(new ServletRequestHeadersAdapter(request));
   }

   private class HeaderNames extends AbstractSet<String> {
      @Override
      public Iterator<String> iterator() {
         return new ServletRequestHeadersAdapter.HeaderNamesIterator(ServletRequestHeadersAdapter.this.request.getHeaderNames());
      }

      @Override
      public int size() {
         Enumeration<String> names = ServletRequestHeadersAdapter.this.request.getHeaderNames();

         int size;
         for (size = 0; names.hasMoreElements(); size++) {
            names.nextElement();
         }

         return size;
      }
   }

   private static final class HeaderNamesIterator implements Iterator<String> {
      private final Enumeration<String> enumeration;

      private HeaderNamesIterator(Enumeration<String> enumeration) {
         this.enumeration = enumeration;
      }

      @Override
      public boolean hasNext() {
         return this.enumeration.hasMoreElements();
      }

      public String next() {
         return this.enumeration.nextElement();
      }

      @Override
      public void remove() {
         throw ServletRequestHeadersAdapter.immutableRequestException();
      }
   }

   private static class RequestHeaderOverrideWrapper implements MultiValueMap<String, String> {
      private final MultiValueMap<String, String> delegate;
      private @Nullable MultiValueMap<String, String> overrideMap;

      RequestHeaderOverrideWrapper(MultiValueMap<String, String> delegate) {
         this.delegate = delegate;
      }

      public @Nullable String getFirst(String key) {
         String value = this.overrideMap != null ? (String)this.overrideMap.getFirst(key) : null;
         return value != null ? value : (String)this.delegate.getFirst(key);
      }

      public void add(String key, @Nullable String value) {
         this.initOverrideMap().add(key, value);
      }

      public void addAll(String key, List<? extends String> values) {
         this.initOverrideMap().addAll(key, values);
      }

      public void addAll(MultiValueMap<String, String> map) {
         throw ServletRequestHeadersAdapter.httpHeadersMapException();
      }

      public void set(String key, @Nullable String value) {
         this.initOverrideMap().set(key, value);
      }

      public void setAll(Map<String, String> map) {
         this.initOverrideMap().setAll(map);
      }

      public Map<String, String> toSingleValueMap() {
         Map<String, String> map = this.delegate.toSingleValueMap();
         if (this.overrideMap != null) {
            this.overrideMap.forEach((key, values) -> map.put(key, (String)values.get(0)));
         }

         return map;
      }

      public int size() {
         if (this.overrideMap == null) {
            return this.delegate.size();
         }

         int size = this.overrideMap.size();

         for (String name : this.delegate.keySet()) {
            if (!this.overrideMap.containsKey(name)) {
               size++;
            }
         }

         return size;
      }

      public boolean isEmpty() {
         return this.delegate.isEmpty() && (this.overrideMap == null || this.overrideMap.isEmpty());
      }

      public boolean containsKey(Object key) {
         if (key instanceof String headerName) {
            if (this.delegate.containsKey(headerName)) {
               return true;
            }

            if (this.overrideMap != null) {
               return this.overrideMap.containsKey(headerName);
            }
         }

         return false;
      }

      public boolean containsValue(Object rawValue) {
         throw ServletRequestHeadersAdapter.httpHeadersMapException();
      }

      public @Nullable List<String> get(Object key) {
         if (key instanceof String headerName) {
            if (this.overrideMap != null) {
               List<String> values = (List<String>)this.overrideMap.get(headerName);
               if (values != null) {
                  return values;
               }
            }

            return (List<String>)this.delegate.get(headerName);
         } else {
            return null;
         }
      }

      public @Nullable List<String> put(String key, List<String> value) {
         return (List<String>)this.initOverrideMap().put(key, value);
      }

      public @Nullable List<String> remove(Object key) {
         return (List<String>)this.initOverrideMap().remove(key);
      }

      public void putAll(Map<? extends String, ? extends List<String>> map) {
         throw ServletRequestHeadersAdapter.httpHeadersMapException();
      }

      public void clear() {
         if (this.overrideMap != null) {
            this.overrideMap.clear();
         }
      }

      public Set<String> keySet() {
         if (this.overrideMap != null) {
            Set<String> set = new LinkedHashSet<>();

            for (String name : this.delegate.keySet()) {
               if (!this.overrideMap.containsKey(name)) {
                  set.add(name);
               }
            }

            set.addAll(this.overrideMap.keySet());
            return set;
         } else {
            return this.delegate.keySet();
         }
      }

      public Collection<List<String>> values() {
         throw ServletRequestHeadersAdapter.httpHeadersMapException();
      }

      public Set<Entry<String, List<String>>> entrySet() {
         throw ServletRequestHeadersAdapter.httpHeadersMapException();
      }

      private MultiValueMap<String, String> initOverrideMap() {
         if (this.overrideMap == null) {
            this.overrideMap = CollectionUtils.toMultiValueMap(new LinkedCaseInsensitiveMap(8, Locale.ROOT));
         }

         return this.overrideMap;
      }

      @Override
      public int hashCode() {
         return this.toMultiValueMap().hashCode();
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other || other instanceof MultiValueMap<?, ?> that && this.toMultiValueMap().equals(that);
      }

      private MultiValueMap<String, String> toMultiValueMap() {
         MultiValueMap<String, String> map = new LinkedMultiValueMap();

         for (String name : this.keySet()) {
            List<String> values = this.get(name);
            if (values != null) {
               for (String value : values) {
                  map.add(name, value);
               }
            }
         }

         return map;
      }

      @Override
      public String toString() {
         return HttpHeaders.formatHeaders(this);
      }
   }
}
