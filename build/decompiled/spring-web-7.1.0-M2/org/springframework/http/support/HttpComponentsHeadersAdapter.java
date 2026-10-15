package org.springframework.http.support;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpMessage;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;
import org.springframework.util.LinkedCaseInsensitiveMap;
import org.springframework.util.MultiValueMap;

public final class HttpComponentsHeadersAdapter implements MultiValueMap<String, String> {
   private final HttpMessage message;
   private final Set<String> headerNames;

   public HttpComponentsHeadersAdapter(HttpMessage message) {
      Assert.notNull(message, "Message must not be null");
      this.message = message;
      this.headerNames = Collections.newSetFromMap(new LinkedCaseInsensitiveMap(message.getHeaders().length, Locale.ROOT));

      for (Header header : message.getHeaders()) {
         this.headerNames.add(header.getName());
      }
   }

   public @Nullable String getFirst(String key) {
      Header header = this.message.getFirstHeader(key);
      return header != null ? header.getValue() : null;
   }

   public void add(String key, @Nullable String value) {
      this.headerNames.add(key);
      this.message.addHeader(key, value);
   }

   public void addAll(String key, List<? extends String> values) {
      values.forEach(value -> this.add(key, value));
   }

   public void addAll(MultiValueMap<String, String> values) {
      values.forEach(this::addAll);
   }

   public void set(String key, @Nullable String value) {
      this.headerNames.add(key);
      this.message.setHeader(key, value);
   }

   public void setAll(Map<String, String> values) {
      values.forEach(this::set);
   }

   public Map<String, String> toSingleValueMap() {
      Map<String, String> map = new LinkedCaseInsensitiveMap(this.message.getHeaders().length, Locale.ROOT);
      this.message.headerIterator().forEachRemaining(h -> map.putIfAbsent(h.getName(), h.getValue()));
      return map;
   }

   public int size() {
      return this.headerNames.size();
   }

   public boolean isEmpty() {
      return this.message.getHeaders().length == 0;
   }

   public boolean containsKey(Object key) {
      return key instanceof String headerName && this.message.containsHeader(headerName);
   }

   public boolean containsValue(Object value) {
      return value instanceof String && Arrays.stream(this.message.getHeaders()).anyMatch(h -> h.getValue().equals(value));
   }

   public @Nullable List<String> get(Object key) {
      List<String> values = null;
      if (this.containsKey(key)) {
         Header[] headers = this.message.getHeaders((String)key);
         values = new ArrayList<>(headers.length);

         for (Header header : headers) {
            values.add(header.getValue());
         }
      }

      return values;
   }

   public @Nullable List<String> put(String key, List<String> values) {
      List<String> oldValues = this.remove(key);
      values.forEach(value -> this.add(key, value));
      return oldValues;
   }

   public @Nullable List<String> remove(Object key) {
      if (key instanceof String headerName) {
         List<String> oldValues = this.get(key);
         this.headerNames.remove(headerName);
         this.message.removeHeaders(headerName);
         return oldValues;
      } else {
         return null;
      }
   }

   public void putAll(Map<? extends String, ? extends List<String>> map) {
      map.forEach(this::put);
   }

   public void clear() {
      this.headerNames.clear();
      this.message.setHeaders(new Header[0]);
   }

   public Set<String> keySet() {
      return new HttpComponentsHeadersAdapter.HeaderNames();
   }

   public Collection<List<String>> values() {
      Collection<List<String>> values = new ArrayList<>(this.message.getHeaders().length);

      for (String headerName : this.keySet()) {
         values.add(this.get(headerName));
      }

      return values;
   }

   public Set<Entry<String, List<String>>> entrySet() {
      return new AbstractSet<Entry<String, List<String>>>() {
         @Override
         public Iterator<Entry<String, List<String>>> iterator() {
            return HttpComponentsHeadersAdapter.this.new EntryIterator();
         }

         @Override
         public int size() {
            return HttpComponentsHeadersAdapter.this.size();
         }
      };
   }

   @Override
   public String toString() {
      return HttpHeaders.formatHeaders(this);
   }

   private class EntryIterator implements Iterator<Entry<String, List<String>>> {
      private final Iterator<String> iterator = HttpComponentsHeadersAdapter.this.keySet().iterator();

      @Override
      public boolean hasNext() {
         return this.iterator.hasNext();
      }

      public Entry<String, List<String>> next() {
         return HttpComponentsHeadersAdapter.this.new HeaderEntry(this.iterator.next());
      }
   }

   private class HeaderEntry implements Entry<String, List<String>> {
      private final String key;

      HeaderEntry(String key) {
         this.key = key;
      }

      public String getKey() {
         return this.key;
      }

      public List<String> getValue() {
         List<String> values = HttpComponentsHeadersAdapter.this.get(this.key);
         return values != null ? values : Collections.emptyList();
      }

      public List<String> setValue(List<String> value) {
         List<String> previousValues = this.getValue();
         HttpComponentsHeadersAdapter.this.put(this.key, value);
         return previousValues;
      }
   }

   private class HeaderNames extends AbstractSet<String> {
      @Override
      public Iterator<String> iterator() {
         return HttpComponentsHeadersAdapter.this.new HeaderNamesIterator(HttpComponentsHeadersAdapter.this.headerNames.iterator());
      }

      @Override
      public int size() {
         return HttpComponentsHeadersAdapter.this.headerNames.size();
      }
   }

   private final class HeaderNamesIterator implements Iterator<String> {
      private final Iterator<String> iterator;
      private @Nullable String currentName;

      private HeaderNamesIterator(Iterator<String> iterator) {
         this.iterator = iterator;
      }

      @Override
      public boolean hasNext() {
         return this.iterator.hasNext();
      }

      public String next() {
         this.currentName = this.iterator.next();
         return this.currentName;
      }

      @Override
      public void remove() {
         if (this.currentName == null) {
            throw new IllegalStateException("No current Header in iterator");
         }

         if (!HttpComponentsHeadersAdapter.this.message.containsHeader(this.currentName)) {
            throw new IllegalStateException("Header not present: " + this.currentName);
         }

         HttpComponentsHeadersAdapter.this.headerNames.remove(this.currentName);
         HttpComponentsHeadersAdapter.this.message.removeHeaders(this.currentName);
      }
   }
}
