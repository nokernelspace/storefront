package org.springframework.http.support;

import io.netty.handler.codec.http.HttpHeaders;
import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.LinkedCaseInsensitiveMap;
import org.springframework.util.MultiValueMap;

public final class Netty4HeadersAdapter implements MultiValueMap<String, String> {
   private final HttpHeaders headers;

   public Netty4HeadersAdapter(HttpHeaders headers) {
      Assert.notNull(headers, "Headers must not be null");
      this.headers = headers;
   }

   public @Nullable String getFirst(String key) {
      return this.headers.get(key);
   }

   public void add(String key, @Nullable String value) {
      if (value != null) {
         this.headers.add(key, value);
      }
   }

   public void addAll(String key, List<? extends String> values) {
      this.headers.add(key, values);
   }

   public void addAll(MultiValueMap<String, String> values) {
      values.forEach(this.headers::add);
   }

   public void set(String key, @Nullable String value) {
      if (value != null) {
         this.headers.set(key, value);
      }
   }

   public void setAll(Map<String, String> values) {
      values.forEach(this.headers::set);
   }

   public Map<String, String> toSingleValueMap() {
      Map<String, String> singleValueMap = new LinkedCaseInsensitiveMap(this.headers.size(), Locale.ROOT);
      this.headers.entries().forEach(entry -> {
         if (!singleValueMap.containsKey(entry.getKey())) {
            singleValueMap.put((String)entry.getKey(), (String)entry.getValue());
         }
      });
      return singleValueMap;
   }

   public int size() {
      return this.headers.names().size();
   }

   public boolean isEmpty() {
      return this.headers.isEmpty();
   }

   public boolean containsKey(Object key) {
      return key instanceof String headerName && this.headers.contains(headerName);
   }

   public boolean containsValue(Object value) {
      return value instanceof String && this.headers.entries().stream().anyMatch(entry -> value.equals(entry.getValue()));
   }

   public @Nullable List<String> get(Object key) {
      return this.containsKey(key) ? this.headers.getAll((String)key) : null;
   }

   public @Nullable List<String> put(String key, @Nullable List<String> value) {
      List<String> previousValues = this.headers.getAll(key);
      this.headers.set(key, value);
      return previousValues;
   }

   public @Nullable List<String> remove(Object key) {
      if (key instanceof String headerName && this.headers.contains(headerName)) {
         List<String> previousValues = this.headers.getAll(headerName);
         this.headers.remove(headerName);
         return previousValues;
      } else {
         return null;
      }
   }

   public void putAll(Map<? extends String, ? extends List<String>> map) {
      map.forEach(this.headers::set);
   }

   public void clear() {
      this.headers.clear();
   }

   public Set<String> keySet() {
      return new Netty4HeadersAdapter.HeaderNames();
   }

   public Collection<List<String>> values() {
      return this.headers.names().stream().<List<String>>map(this.headers::getAll).toList();
   }

   public Set<Entry<String, List<String>>> entrySet() {
      return new AbstractSet<Entry<String, List<String>>>() {
         @Override
         public Iterator<Entry<String, List<String>>> iterator() {
            return Netty4HeadersAdapter.this.new EntryIterator();
         }

         @Override
         public int size() {
            return Netty4HeadersAdapter.this.headers.size();
         }
      };
   }

   @Override
   public String toString() {
      return org.springframework.http.HttpHeaders.formatHeaders(this);
   }

   private class EntryIterator implements Iterator<Entry<String, List<String>>> {
      private final Iterator<String> names = Netty4HeadersAdapter.this.headers.names().iterator();

      @Override
      public boolean hasNext() {
         return this.names.hasNext();
      }

      public Entry<String, List<String>> next() {
         return Netty4HeadersAdapter.this.new HeaderEntry(this.names.next());
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
         return Netty4HeadersAdapter.this.headers.getAll(this.key);
      }

      public List<String> setValue(List<String> value) {
         List<String> previousValues = Netty4HeadersAdapter.this.headers.getAll(this.key);
         Netty4HeadersAdapter.this.headers.set(this.key, value);
         return previousValues;
      }
   }

   private class HeaderNames extends AbstractSet<String> {
      @Override
      public Iterator<String> iterator() {
         return Netty4HeadersAdapter.this.new HeaderNamesIterator(Netty4HeadersAdapter.this.headers.names().iterator());
      }

      @Override
      public int size() {
         return Netty4HeadersAdapter.this.headers.names().size();
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

         if (!Netty4HeadersAdapter.this.headers.contains(this.currentName)) {
            throw new IllegalStateException("Header not present: " + this.currentName);
         }

         Netty4HeadersAdapter.this.headers.remove(this.currentName);
      }
   }
}
