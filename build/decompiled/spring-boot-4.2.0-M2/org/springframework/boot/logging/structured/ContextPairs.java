package org.springframework.boot.logging.structured;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.json.JsonWriter;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class ContextPairs {
   private final boolean include;
   private final String prefix;

   ContextPairs(boolean include, @Nullable String prefix) {
      this.include = include;
      this.prefix = prefix != null ? prefix : "";
   }

   public <T> BiConsumer<T, BiConsumer<String, Object>> flat(String delimiter, Consumer<ContextPairs.Pairs<T>> pairs) {
      return this.flat(this.joinWith(delimiter), pairs);
   }

   public <T> BiConsumer<T, BiConsumer<String, Object>> flat(ContextPairs.Joiner joiner, Consumer<ContextPairs.Pairs<T>> pairs) {
      return !this.include ? this.none() : new ContextPairs.Pairs<>(joiner, pairs)::flat;
   }

   public <T> BiConsumer<T, BiConsumer<String, Object>> nested(Consumer<ContextPairs.Pairs<T>> pairs) {
      return !this.include ? this.none() : new ContextPairs.Pairs<>(this.joinWith("."), pairs)::nested;
   }

   private <T, V> BiConsumer<T, BiConsumer<String, V>> none() {
      return (item, pairs) -> {};
   }

   private ContextPairs.Joiner joinWith(String delimiter) {
      return (prefix, name) -> {
         StringBuilder joined = new StringBuilder(prefix.length() + delimiter.length() + name.length());
         joined.append(prefix);
         if (!prefix.isEmpty() && !prefix.endsWith(delimiter) && !name.startsWith(delimiter)) {
            joined.append(delimiter);
         }

         joined.append(name);
         return joined.toString();
      };
   }

   @FunctionalInterface
   public interface Joiner {
      @Nullable String join(String prefix, String name);
   }

   public class Pairs<T> {
      private final ContextPairs.Joiner joiner;
      private final List<BiConsumer<T, BiConsumer<String, ?>>> addedPairs;

      Pairs(ContextPairs.Joiner joiner, Consumer<ContextPairs.Pairs<T>> pairs) {
         this.joiner = joiner;
         this.addedPairs = new ArrayList<>();
         pairs.accept(this);
      }

      public <V> void addMapEntries(Function<T, Map<String, V>> extractor) {
         Function<T, Iterable<Entry<String, V>>> elementsExtractor = extractor.andThen(Map::entrySet);
         this.add(elementsExtractor, Entry::getKey, Entry::getValue);
      }

      public <E> void add(Function<T, @Nullable Iterable<E>> elementsExtractor, JsonWriter.PairExtractor<E> pairExtractor) {
         this.add(elementsExtractor, pairExtractor::getName, pairExtractor::getValue);
      }

      public <E, V> void add(Function<T, @Nullable Iterable<E>> elementsExtractor, Function<E, String> nameExtractor, Function<E, V> valueExtractor) {
         this.add((item, pairs) -> {
            Iterable<E> elements = elementsExtractor.apply(item);
            if (elements != null) {
               elements.forEach(element -> {
                  String name = nameExtractor.apply((E)element);
                  V value = valueExtractor.apply((E)element);
                  pairs.accept(name, value);
               });
            }
         });
      }

      public <V> void add(BiConsumer<T, BiConsumer<String, V>> pairs) {
         this.addedPairs.add(pairs);
      }

      void flat(T item, BiConsumer<String, Object> pairs) {
         this.addedPairs.forEach(action -> action.accept(item, this.joining(pairs)));
      }

      void nested(T item, BiConsumer<String, Object> pairs) {
         LinkedHashMap<String, Object> result = new LinkedHashMap<>();
         this.addedPairs.forEach(addedPair -> addedPair.accept(item, this.joining((name, value) -> {
            StringBuilder part = new StringBuilder(name.length());
            int length = !name.endsWith(".") ? name.length() : name.length() - 1;
            Map<String, Object> destination = result;

            for (int i = 0; i < length; i++) {
               char ch = name.charAt(i);
               if (i == length - 1) {
                  part.append(ch);
                  Object previous = destination.put(part.toString(), value);
                  this.assertNotDuplicateNestedPairs(previous == null, name, length);
               } else if (ch == '.') {
                  Object current = destination.computeIfAbsent(part.toString(), key -> new LinkedHashMap());
                  this.assertNotDuplicateNestedPairs(current instanceof Map, name, i);
                  destination = (Map<String, Object>)current;
                  part.setLength(0);
               } else {
                  part.append(ch);
               }
            }
         })));
         result.forEach(pairs);
      }

      private void assertNotDuplicateNestedPairs(boolean expression, String name, int index) {
         Assert.state(expression, () -> "Duplicate nested pairs added under '%s'".formatted(name.substring(0, index)));
      }

      private <V> BiConsumer<String, V> joining(BiConsumer<String, V> pairs) {
         return (name, value) -> {
            name = this.joiner.join(ContextPairs.this.prefix, name != null ? name : "");
            if (StringUtils.hasLength(name)) {
               pairs.accept(name, value);
            }
         };
      }
   }
}
