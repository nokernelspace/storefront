package org.springframework.util.xml;

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.xml.namespace.NamespaceContext;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public class SimpleNamespaceContext implements NamespaceContext {
   private final Map<String, String> prefixToNamespaceUri = new HashMap<>();
   private final Map<String, Set<String>> namespaceUriToPrefixes = new HashMap<>();
   private String defaultNamespaceUri = "";

   @Override
   public String getNamespaceURI(String prefix) {
      Assert.notNull(prefix, "'prefix' must not be null");
      if ("xml".equals(prefix)) {
         return "http://www.w3.org/XML/1998/namespace";
      } else if ("xmlns".equals(prefix)) {
         return "http://www.w3.org/2000/xmlns/";
      } else if ("".equals(prefix)) {
         return this.defaultNamespaceUri;
      } else {
         return this.prefixToNamespaceUri.containsKey(prefix) ? this.prefixToNamespaceUri.get(prefix) : "";
      }
   }

   @Override
   public @Nullable String getPrefix(String namespaceUri) {
      Set<String> prefixes = this.getPrefixesSet(namespaceUri);
      return !prefixes.isEmpty() ? prefixes.iterator().next() : null;
   }

   @Override
   public Iterator<String> getPrefixes(String namespaceUri) {
      return this.getPrefixesSet(namespaceUri).iterator();
   }

   private Set<String> getPrefixesSet(String namespaceUri) {
      Assert.notNull(namespaceUri, "'namespaceUri' must not be null");
      if (this.defaultNamespaceUri.equals(namespaceUri)) {
         return Collections.singleton("");
      }

      if ("http://www.w3.org/XML/1998/namespace".equals(namespaceUri)) {
         return Collections.singleton("xml");
      }

      if ("http://www.w3.org/2000/xmlns/".equals(namespaceUri)) {
         return Collections.singleton("xmlns");
      }

      Set<String> prefixes = this.namespaceUriToPrefixes.get(namespaceUri);
      return prefixes != null ? Collections.unmodifiableSet(prefixes) : Collections.emptySet();
   }

   public void setBindings(Map<String, String> bindings) {
      bindings.forEach(this::bindNamespaceUri);
   }

   public void bindDefaultNamespaceUri(String namespaceUri) {
      this.bindNamespaceUri("", namespaceUri);
   }

   public void bindNamespaceUri(String prefix, String namespaceUri) {
      Assert.notNull(prefix, "'prefix' must not be null");
      Assert.notNull(namespaceUri, "'namespaceUri' must not be null");
      if ("".equals(prefix)) {
         this.defaultNamespaceUri = namespaceUri;
      } else {
         this.prefixToNamespaceUri.put(prefix, namespaceUri);
         Set<String> prefixes = this.namespaceUriToPrefixes.computeIfAbsent(namespaceUri, key -> new LinkedHashSet<>());
         prefixes.add(prefix);
      }
   }

   public void removeBinding(@Nullable String prefix) {
      if ("".equals(prefix)) {
         this.defaultNamespaceUri = "";
      } else if (prefix != null) {
         String namespaceUri = this.prefixToNamespaceUri.remove(prefix);
         if (namespaceUri != null) {
            Set<String> prefixes = this.namespaceUriToPrefixes.get(namespaceUri);
            if (prefixes != null) {
               prefixes.remove(prefix);
               if (prefixes.isEmpty()) {
                  this.namespaceUriToPrefixes.remove(namespaceUri);
               }
            }
         }
      }
   }

   public void clear() {
      this.prefixToNamespaceUri.clear();
      this.namespaceUriToPrefixes.clear();
   }

   public Iterator<String> getBoundPrefixes() {
      return this.prefixToNamespaceUri.keySet().iterator();
   }
}
