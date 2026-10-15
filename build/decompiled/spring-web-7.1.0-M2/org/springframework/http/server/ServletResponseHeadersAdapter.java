package org.springframework.http.server;

import jakarta.servlet.http.HttpServletResponse;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

class ServletResponseHeadersAdapter implements MultiValueMap<String, String> {
   private final HttpServletResponse response;

   ServletResponseHeadersAdapter(HttpServletResponse response) {
      this.response = response;
   }

   public @Nullable String getFirst(String key) {
      String header = this.response.getHeader(key);
      if (header == null && key.equalsIgnoreCase("Content-Type")) {
         header = this.response.getContentType();
      }

      return header;
   }

   public void add(String key, @Nullable String value) {
      this.response.addHeader(key, value);
   }

   public void addAll(String key, List<? extends String> values) {
      for (String value : values) {
         this.response.addHeader(key, value);
      }
   }

   public void addAll(MultiValueMap<String, String> map) {
      throw httpHeadersUnsupportedOperationException();
   }

   public void set(String key, @Nullable String value) {
      this.response.setHeader(key, value);
   }

   public void setAll(Map<String, String> map) {
      for (Entry<String, String> entry : map.entrySet()) {
         this.response.setHeader(entry.getKey(), entry.getValue());
      }
   }

   public Map<String, String> toSingleValueMap() {
      Map<String, String> map = new LinkedHashMap<>();

      for (String name : this.response.getHeaderNames()) {
         map.put(name, this.response.getHeader(name));
      }

      return map;
   }

   public int size() {
      return this.response.getHeaderNames().size();
   }

   public boolean isEmpty() {
      return this.response.getHeaderNames().isEmpty();
   }

   public boolean containsKey(Object key) {
      return key instanceof String headerName ? this.response.containsHeader(headerName) : false;
   }

   public boolean containsValue(Object rawValue) {
      throw httpHeadersUnsupportedOperationException();
   }

   public @Nullable List<String> get(Object key) {
      if (key instanceof String headerName) {
         Collection<String> values = this.response.getHeaders(headerName);
         if (values.isEmpty() && headerName.equalsIgnoreCase("Content-Type")) {
            String contentType = this.response.getContentType();
            return contentType != null ? Collections.singletonList(contentType) : null;
         }

         if (!values.isEmpty()) {
            return new ArrayList<>(values);
         }
      }

      return null;
   }

   public @Nullable List<String> put(String key, List<String> values) {
      List<String> previous = this.remove(key);

      for (String value : values) {
         this.response.addHeader(key, value);
      }

      return previous;
   }

   public @Nullable List<String> remove(Object key) {
      if (key instanceof String headerName) {
         List<String> previous = this.get(headerName);
         if (previous != null) {
            this.response.setHeader(headerName, null);
         }

         return previous;
      } else {
         return null;
      }
   }

   public void putAll(Map<? extends String, ? extends List<String>> map) {
      throw httpHeadersUnsupportedOperationException();
   }

   public void clear() {
      for (String headerName : this.response.getHeaderNames()) {
         this.response.setHeader(headerName, null);
      }
   }

   public Set<String> keySet() {
      return new ServletResponseHeadersAdapter.HeaderNames();
   }

   public Collection<List<String>> values() {
      throw httpHeadersUnsupportedOperationException();
   }

   public Set<Entry<String, List<String>>> entrySet() {
      throw httpHeadersUnsupportedOperationException();
   }

   private static UnsupportedOperationException httpHeadersUnsupportedOperationException() {
      return new UnsupportedOperationException("HttpHeaders does not support all Map operations");
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

      for (String name : this.response.getHeaderNames()) {
         for (String value : this.response.getHeaders(name)) {
            map.add(name, value);
         }
      }

      return map;
   }

   @Override
   public String toString() {
      return HttpHeaders.formatHeaders(this);
   }

   private class HeaderNames extends AbstractSet<String> {
      @Override
      public Iterator<String> iterator() {
         return new ServletResponseHeadersAdapter.HeaderNamesIterator(ServletResponseHeadersAdapter.this.response.getHeaderNames());
      }

      @Override
      public int size() {
         return ServletResponseHeadersAdapter.this.size();
      }
   }

   private static final class HeaderNamesIterator implements Iterator<String> {
      private final Iterator<String> values;

      private HeaderNamesIterator(Collection<String> values) {
         this.values = values.iterator();
      }

      @Override
      public boolean hasNext() {
         return this.values.hasNext();
      }

      public String next() {
         return this.values.next();
      }

      @Override
      public void remove() {
         throw new UnsupportedOperationException();
      }
   }
}
