package org.springframework.boot.json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.util.LambdaSafe;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.function.ThrowingConsumer;

class JsonValueWriter {
   private static final int DEFAULT_MAX_NESTING_DEPTH = 500;
   private final Appendable out;
   private final int maxNestingDepth;
   private JsonWriter.MemberPath path = JsonWriter.MemberPath.ROOT;
   private final Deque<JsonWriterFiltersAndProcessors> filtersAndProcessors = new ArrayDeque<>();
   private final Deque<JsonValueWriter.ActiveSeries> activeSeries = new ArrayDeque<>();

   JsonValueWriter(Appendable out) {
      this(out, 500);
   }

   JsonValueWriter(Appendable out, int maxNestingDepth) {
      this.out = out;
      this.maxNestingDepth = maxNestingDepth;
   }

   void pushProcessors(JsonWriterFiltersAndProcessors jsonProcessors) {
      this.filtersAndProcessors.addLast(jsonProcessors);
   }

   void popProcessors() {
      this.filtersAndProcessors.removeLast();
   }

   <N, V> void write(@Nullable N name, @Nullable V value) {
      if (name != null) {
         this.writePair(name, value);
      } else {
         this.write(value);
      }
   }

   <V> void write(@Nullable V value) {
      value = this.processValue(value);
      if (value == null) {
         this.append("null");
      } else if (value instanceof WritableJson writableJson) {
         try {
            writableJson.to(this.out);
         } catch (IOException ex) {
            throw new UncheckedIOException(ex);
         }
      } else if (value instanceof Iterable<?> iterable && this.canWriteAsArray(iterable)) {
         this.writeArray(iterable::forEach);
      } else if (ObjectUtils.isArray(value)) {
         this.writeArray(Arrays.asList(ObjectUtils.toObjectArray(value))::forEach);
      } else if (value instanceof Map<?, ?> map) {
         this.writeObject(map::forEach);
      } else if (!(value instanceof Number) && !(value instanceof Boolean)) {
         this.writeString(value);
      } else {
         this.append(value.toString());
      }
   }

   private boolean canWriteAsArray(Iterable<?> iterable) {
      return !(iterable instanceof Path);
   }

   void start(JsonValueWriter.@Nullable Series series) {
      if (series != null) {
         int nestingDepth = this.activeSeries.size();
         Assert.state(
            nestingDepth <= this.maxNestingDepth,
            () -> "JSON nesting depth (%s) exceeds maximum depth of %s (current path: %s)".formatted(nestingDepth, this.maxNestingDepth, this.path)
         );
         this.activeSeries.push(new JsonValueWriter.ActiveSeries(series));
         this.append(series.openChar);
      }
   }

   void end(JsonValueWriter.@Nullable Series series) {
      if (series != null) {
         this.activeSeries.pop();
         this.append(series.closeChar);
      }
   }

   <E> void writeArray(Consumer<Consumer<E>> elements) {
      this.start(JsonValueWriter.Series.ARRAY);
      elements.accept(ThrowingConsumer.of(this::writeElement));
      this.end(JsonValueWriter.Series.ARRAY);
   }

   <E> void writeElements(Consumer<Consumer<E>> elements) {
      elements.accept(ThrowingConsumer.of(this::writeElement));
   }

   <E> void writeElement(E element) {
      JsonValueWriter.ActiveSeries activeSeries = this.activeSeries.peek();
      Assert.state(activeSeries != null, "No series has been started");
      this.path = activeSeries.updatePath(this.path);
      activeSeries.incrementIndexAndAddCommaIfRequired();
      this.write(element);
      this.path = activeSeries.restorePath(this.path);
   }

   <N, V> void writeObject(Consumer<BiConsumer<N, V>> pairs) {
      this.start(JsonValueWriter.Series.OBJECT);
      pairs.accept(this::writePair);
      this.end(JsonValueWriter.Series.OBJECT);
   }

   <N, V> void writePairs(Consumer<BiConsumer<N, V>> pairs) {
      pairs.accept(this::writePair);
   }

   private <N, V> void writePair(N name, @Nullable V value) {
      this.path = this.path.child(name.toString());
      if (!this.isFilteredPath()) {
         String processedName = this.processName(name.toString());
         JsonValueWriter.ActiveSeries activeSeries = this.activeSeries.peek();
         Assert.state(activeSeries != null, "No series has been started");
         activeSeries.incrementIndexAndAddCommaIfRequired();
         Assert.state(activeSeries.addName(processedName), () -> "The name '" + processedName + "' has already been written");
         this.writeString(processedName);
         this.append(":");
         this.write(value);
      }

      this.path = this.path.parent() != null ? this.path.parent() : JsonWriter.MemberPath.ROOT;
   }

   private void writeString(Object value) {
      try {
         this.out.append('"');
         String string = value.toString();

         for (int i = 0; i < string.length(); i++) {
            char ch = string.charAt(i);
            switch (ch) {
               case '\b':
                  this.out.append("\\b");
                  break;
               case '\t':
                  this.out.append("\\t");
                  break;
               case '\n':
                  this.out.append("\\n");
                  break;
               case '\f':
                  this.out.append("\\f");
                  break;
               case '\r':
                  this.out.append("\\r");
                  break;
               case '"':
                  this.out.append("\\\"");
                  break;
               case '\\':
                  this.out.append("\\\\");
                  break;
               default:
                  if (Character.isISOControl(ch)) {
                     this.out.append("\\u");
                     this.out.append(String.format("%04X", Integer.valueOf(ch)));
                  } else {
                     this.out.append(ch);
                  }
            }
         }

         this.out.append('"');
      } catch (IOException ex) {
         throw new UncheckedIOException(ex);
      }
   }

   private void append(String value) {
      try {
         this.out.append(value);
      } catch (IOException ex) {
         throw new UncheckedIOException(ex);
      }
   }

   private void append(char ch) {
      try {
         this.out.append(ch);
      } catch (IOException ex) {
         throw new UncheckedIOException(ex);
      }
   }

   private boolean isFilteredPath() {
      for (JsonWriterFiltersAndProcessors filtersAndProcessors : this.filtersAndProcessors) {
         for (Predicate<JsonWriter.MemberPath> pathFilter : filtersAndProcessors.pathFilters()) {
            if (pathFilter.test(this.path)) {
               return true;
            }
         }
      }

      return false;
   }

   private String processName(String name) {
      for (JsonWriterFiltersAndProcessors filtersAndProcessors : this.filtersAndProcessors) {
         for (JsonWriter.NameProcessor nameProcessor : filtersAndProcessors.nameProcessors()) {
            name = this.processName(name, nameProcessor);
         }
      }

      return name;
   }

   private String processName(String name, JsonWriter.NameProcessor nameProcessor) {
      name = nameProcessor.processName(this.path, name);
      Assert.state(StringUtils.hasLength(name), "NameProcessor " + nameProcessor + " returned an empty result");
      return name;
   }

   private <V> @Nullable V processValue(@Nullable V value) {
      for (JsonWriterFiltersAndProcessors filtersAndProcessors : this.filtersAndProcessors) {
         for (JsonWriter.ValueProcessor<?> valueProcessor : filtersAndProcessors.valueProcessors()) {
            value = this.processValue(value, valueProcessor);
         }
      }

      return value;
   }

   private <V> V processValue(V value, JsonWriter.ValueProcessor<?> valueProcessor) {
      return LambdaSafe.callback(JsonWriter.ValueProcessor.class, valueProcessor, this.path, value)
         .invokeAnd(call -> call.processValue(this.path, value))
         .get(value);
   }

   private final class ActiveSeries {
      private final JsonValueWriter.Series series;
      private int index;
      private final Set<String> names = new HashSet<>();

      private ActiveSeries(JsonValueWriter.Series series) {
         this.series = series;
      }

      boolean addName(String processedName) {
         return this.names.add(processedName);
      }

      JsonWriter.MemberPath updatePath(JsonWriter.MemberPath path) {
         return this.series != JsonValueWriter.Series.ARRAY ? path : path.child(this.index);
      }

      JsonWriter.MemberPath restorePath(JsonWriter.MemberPath path) {
         if (this.series != JsonValueWriter.Series.ARRAY) {
            return path;
         } else {
            return path.parent() != null ? path.parent() : JsonWriter.MemberPath.ROOT;
         }
      }

      void incrementIndexAndAddCommaIfRequired() {
         if (this.index > 0) {
            JsonValueWriter.this.append(',');
         }

         this.index++;
      }
   }

   enum Series {
      OBJECT('{', '}'),
      ARRAY('[', ']');

      final char openChar;
      final char closeChar;

      Series(char openChar, char closeChar) {
         this.openChar = openChar;
         this.closeChar = closeChar;
      }
   }
}
