package org.springframework.http.support;

import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpFields.Mutable;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.util.Assert;
import org.springframework.util.LinkedCaseInsensitiveMap;
import org.springframework.util.MultiValueMap;

public final class JettyHeadersAdapter implements MultiValueMap<String, String> {
   private final HttpFields headers;
   private final @Nullable Mutable mutable;

   public JettyHeadersAdapter(HttpFields headers) {
      Assert.notNull(headers, "Headers must not be null");
      this.headers = headers;
      this.mutable = headers instanceof Mutable m ? m : null;
   }

   public String getFirst(String key) {
      return this.headers.get(key);
   }

   public void add(String key, @Nullable String value) {
      if (value != null) {
         Mutable mutableHttpFields = this.mutableFields();
         mutableHttpFields.add(key, value);
      }
   }

   public void addAll(String key, List<? extends String> values) {
      values.forEach(value -> this.add(key, value));
   }

   public void addAll(MultiValueMap<String, String> values) {
      values.forEach(this::addAll);
   }

   public void set(String key, @Nullable String value) {
      Mutable mutableHttpFields = this.mutableFields();
      if (value != null) {
         mutableHttpFields.put(key, value);
      } else {
         mutableHttpFields.remove(key);
      }
   }

   public void setAll(Map<String, String> values) {
      values.forEach(this::set);
   }

   public Map<String, String> toSingleValueMap() {
      Map<String, String> singleValueMap = new LinkedCaseInsensitiveMap(this.headers.size(), Locale.ROOT);
      Iterator<HttpField> iterator = this.headers.iterator();
      iterator.forEachRemaining(field -> {
         if (!singleValueMap.containsKey(field.getName())) {
            singleValueMap.put(field.getName(), field.getValue());
         }
      });
      return singleValueMap;
   }

   public int size() {
      return this.headers.getFieldNamesCollection().size();
   }

   public boolean isEmpty() {
      return this.headers.size() == 0;
   }

   public boolean containsKey(Object key) {
      return key instanceof String name && this.headers.contains(name);
   }

   public boolean containsValue(Object value) {
      if (value instanceof String searchString) {
         for (HttpField field : this.headers) {
            if (field.contains(searchString)) {
               return true;
            }
         }
      }

      return false;
   }

   public @Nullable List<String> get(Object key) {
      List<String> list = null;
      if (key instanceof String name) {
         for (HttpField field : this.headers) {
            if (field.is(name)) {
               if (list == null) {
                  list = new ArrayList<>();
               }

               list.add(field.getValue());
            }
         }
      }

      return list;
   }

   public @Nullable List<String> put(String key, List<String> value) {
      Mutable mutableHttpFields = this.mutableFields();
      List<String> oldValues = this.get(key);
      if (oldValues == null) {
         switch (value.size()) {
            case 0:
               break;
            case 1:
               mutableHttpFields.add(key, value.get(0));
               break;
            default:
               mutableHttpFields.add(key, value);
         }
      } else {
         switch (value.size()) {
            case 0:
               mutableHttpFields.remove(key);
               break;
            case 1:
               mutableHttpFields.put(key, value.get(0));
               break;
            default:
               mutableHttpFields.put(key, value);
         }
      }

      return oldValues;
   }

   public @Nullable List<String> remove(Object key) {
      Mutable mutableHttpFields = this.mutableFields();
      List<String> list = null;
      if (key instanceof String name) {
         ListIterator<HttpField> i = mutableHttpFields.listIterator();

         while (i.hasNext()) {
            HttpField f = i.next();
            if (f.is(name)) {
               if (list == null) {
                  list = new ArrayList<>();
               }

               list.add(f.getValue());
               i.remove();
            }
         }
      }

      return list;
   }

   public void putAll(Map<? extends String, ? extends List<String>> map) {
      map.forEach(this::put);
   }

   public void clear() {
      Mutable mutableHttpFields = this.mutableFields();
      mutableHttpFields.clear();
   }

   public Set<String> keySet() {
      return new JettyHeadersAdapter.HeaderNames();
   }

   public Collection<List<String>> values() {
      return this.headers.getFieldNamesCollection().stream().<List<String>>map(this.headers::getValuesList).toList();
   }

   public Set<Entry<String, List<String>>> entrySet() {
      return new AbstractSet<Entry<String, List<String>>>() {
         @Override
         public Iterator<Entry<String, List<String>>> iterator() {
            return JettyHeadersAdapter.this.new EntryIterator();
         }

         @Override
         public int size() {
            return JettyHeadersAdapter.this.headers.getFieldNamesCollection().size();
         }
      };
   }

   private Mutable mutableFields() {
      if (this.mutable == null) {
         throw new IllegalStateException("Immutable headers");
      } else {
         return this.mutable;
      }
   }

   @Override
   public String toString() {
      return HttpHeaders.formatHeaders(this);
   }

   private class EntryIterator implements Iterator<Entry<String, List<String>>> {
      private final Iterator<String> names = JettyHeadersAdapter.this.headers.getFieldNamesCollection().iterator();

      @Override
      public boolean hasNext() {
         return this.names.hasNext();
      }

      public Entry<String, List<String>> next() {
         return JettyHeadersAdapter.this.new HeaderEntry(this.names.next());
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
         return JettyHeadersAdapter.this.headers.getValuesList(this.key);
      }

      public List<String> setValue(List<String> value) {
         Mutable mutableHttpFields = JettyHeadersAdapter.this.mutableFields();
         List<String> previousValues = JettyHeadersAdapter.this.headers.getValuesList(this.key);
         mutableHttpFields.put(this.key, value);
         return previousValues;
      }
   }

   private class HeaderNames extends AbstractSet<String> {
      @Override
      public Iterator<String> iterator() {
         return JettyHeadersAdapter.this.new HeaderNamesIterator(JettyHeadersAdapter.this.headers.getFieldNamesCollection().iterator());
      }

      @Override
      public int size() {
         return JettyHeadersAdapter.this.headers.getFieldNamesCollection().size();
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
         Mutable mutableHttpFields = JettyHeadersAdapter.this.mutableFields();
         if (this.currentName == null) {
            throw new IllegalStateException("No current Header in iterator");
         }

         if (!JettyHeadersAdapter.this.headers.contains(this.currentName)) {
            throw new IllegalStateException("Header not present: " + this.currentName);
         }

         mutableHttpFields.remove(this.currentName);
      }
   }
}
