package org.springframework.http.server.reactive;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.MimeHeaders;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;

class TomcatHeadersAdapter implements MultiValueMap<String, String> {
   private final MimeHeaders headers;

   TomcatHeadersAdapter(MimeHeaders headers) {
      this.headers = headers;
   }

   public String getFirst(String key) {
      return this.headers.getHeader(key);
   }

   public void add(String key, @Nullable String value) {
      this.headers.addValue(key).setString(value);
   }

   public void addAll(String key, List<? extends String> values) {
      for (String value : values) {
         this.add(key, value);
      }
   }

   public void addAll(MultiValueMap<String, String> values) {
      for (Entry<String, List<String>> entry : values.entrySet()) {
         this.addAll(entry.getKey(), entry.getValue());
      }
   }

   public void set(String key, @Nullable String value) {
      this.headers.setValue(key).setString(value);
   }

   public void setAll(Map<String, String> values) {
      for (Entry<String, String> entry : values.entrySet()) {
         this.set(entry.getKey(), entry.getValue());
      }
   }

   public Map<String, String> toSingleValueMap() {
      Map<String, String> map = CollectionUtils.newLinkedHashMap(this.headers.size());

      for (String name : this.keySet()) {
         map.put(name, this.getFirst(name));
      }

      return map;
   }

   public int size() {
      Enumeration<String> names = this.headers.names();
      Set<String> set = new LinkedHashSet<>(this.headers.size());

      while (names.hasMoreElements()) {
         set.add(names.nextElement().toLowerCase(Locale.ROOT));
      }

      return set.size();
   }

   public boolean isEmpty() {
      return this.headers.size() == 0;
   }

   public boolean containsKey(Object key) {
      return key instanceof String headerName ? this.headers.findHeader(headerName, 0) != -1 : false;
   }

   public boolean containsValue(Object value) {
      if (value instanceof String text) {
         MessageBytes bytes = MessageBytes.newInstance();
         bytes.setString(text);

         for (int i = 0; i < this.headers.size(); i++) {
            if (this.headers.getValue(i).equals(bytes)) {
               return true;
            }
         }
      }

      return false;
   }

   public @Nullable List<String> get(Object key) {
      if (key instanceof String headerName) {
         Enumeration<String> values = this.headers.values(headerName);
         if (values.hasMoreElements()) {
            return Collections.list(values);
         }
      }

      return null;
   }

   public @Nullable List<String> put(String key, List<String> value) {
      List<String> previous = this.get(key);
      this.headers.removeHeader(key);
      value.forEach(v -> this.headers.addValue(key).setString(v));
      return previous;
   }

   public @Nullable List<String> remove(Object key) {
      if (key instanceof String headerName) {
         List<String> previous = this.get(key);
         this.headers.removeHeader(headerName);
         return previous;
      } else {
         return null;
      }
   }

   public void putAll(Map<? extends String, ? extends List<String>> map) {
      for (Entry<? extends String, ? extends List<String>> entry : map.entrySet()) {
         this.put(entry.getKey(), (List<String>)entry.getValue());
      }
   }

   public void clear() {
      this.headers.recycle();
   }

   public Set<String> keySet() {
      return new TomcatHeadersAdapter.HeaderNames();
   }

   public Collection<List<String>> values() {
      return this.keySet().stream().map(this::get).toList();
   }

   public Set<Entry<String, List<String>>> entrySet() {
      return new AbstractSet<Entry<String, List<String>>>() {
         @Override
         public Iterator<Entry<String, List<String>>> iterator() {
            return TomcatHeadersAdapter.this.new EntryIterator();
         }

         @Override
         public int size() {
            return TomcatHeadersAdapter.this.size();
         }
      };
   }

   @Override
   public String toString() {
      return HttpHeaders.formatHeaders(this);
   }

   private class EntryIterator implements Iterator<Entry<String, List<String>>> {
      private final Enumeration<String> names = TomcatHeadersAdapter.this.headers.names();

      @Override
      public boolean hasNext() {
         return this.names.hasMoreElements();
      }

      public Entry<String, List<String>> next() {
         return TomcatHeadersAdapter.this.new HeaderEntry(this.names.nextElement());
      }
   }

   private final class HeaderEntry implements Entry<String, List<String>> {
      private final String key;

      HeaderEntry(String key) {
         this.key = key;
      }

      public String getKey() {
         return this.key;
      }

      public @Nullable List<String> getValue() {
         return TomcatHeadersAdapter.this.get(this.key);
      }

      public @Nullable List<String> setValue(List<String> value) {
         List<String> previous = this.getValue();
         TomcatHeadersAdapter.this.headers.removeHeader(this.key);
         TomcatHeadersAdapter.this.addAll(this.key, value);
         return previous;
      }
   }

   private class HeaderNames extends AbstractSet<String> {
      @Override
      public Iterator<String> iterator() {
         return TomcatHeadersAdapter.this.new HeaderNamesIterator(TomcatHeadersAdapter.this.headers.names());
      }

      @Override
      public int size() {
         Enumeration<String> names = TomcatHeadersAdapter.this.headers.names();

         int size;
         for (size = 0; names.hasMoreElements(); size++) {
            names.nextElement();
         }

         return size;
      }
   }

   private final class HeaderNamesIterator implements Iterator<String> {
      private final Enumeration<String> enumeration;
      private @Nullable String currentName;

      private HeaderNamesIterator(Enumeration<String> enumeration) {
         this.enumeration = enumeration;
      }

      @Override
      public boolean hasNext() {
         return this.enumeration.hasMoreElements();
      }

      public String next() {
         this.currentName = this.enumeration.nextElement();
         return this.currentName;
      }

      @Override
      public void remove() {
         if (this.currentName == null) {
            throw new IllegalStateException("No current Header in iterator");
         }

         boolean found = false;

         for (int i = 0; i < TomcatHeadersAdapter.this.headers.size(); i++) {
            if (TomcatHeadersAdapter.this.headers.getName(i).equalsIgnoreCase(this.currentName)) {
               TomcatHeadersAdapter.this.headers.removeHeader(i--);
               found = true;
            }
         }

         if (!found) {
            throw new IllegalStateException("Header not present: " + this.currentName);
         }
      }
   }
}
