package org.springframework.boot.json;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

@FunctionalInterface
public interface JsonWriter<T> {
   void write(@Nullable T instance, Appendable out) throws IOException;

   default String writeToString(@Nullable T instance) {
      return this.write(instance).toJsonString();
   }

   default WritableJson write(@Nullable T instance) {
      return WritableJson.of(out -> this.write(instance, out));
   }

   default JsonWriter<T> withNewLineAtEnd() {
      return this.withSuffix("\n");
   }

   default JsonWriter<T> withSuffix(@Nullable String suffix) {
      return !StringUtils.hasLength(suffix) ? this : (instance, out) -> {
         this.write(instance, out);
         out.append(suffix);
      };
   }

   static <T> JsonWriter<T> standard() {
      return of(JsonWriter.Members::add);
   }

   static <T> JsonWriter<T> of(Consumer<JsonWriter.Members<T>> members) {
      JsonWriter.Members<T> initializedMembers = new JsonWriter.Members<>(members, false);
      return (instance, out) -> initializedMembers.write(instance, new JsonValueWriter(out));
   }

   @FunctionalInterface
   interface Extractor<T, R> {
      @Nullable R extract(@NonNull T value);
   }

   final class Member<T> {
      private final int index;
      private final @Nullable String name;
      private JsonWriter.Member.ValueExtractor<? extends @Nullable T> valueExtractor;
      private @Nullable BiConsumer<T, BiConsumer<?, ?>> pairs;
      private JsonWriter.@Nullable Members<T> members;

      Member(int index, @Nullable String name, JsonWriter.Member.ValueExtractor<? extends @Nullable T> valueExtractor) {
         this.index = index;
         this.name = name;
         this.valueExtractor = valueExtractor;
      }

      public JsonWriter.Member<T> whenNotNull() {
         return this.when(Objects::nonNull);
      }

      public JsonWriter.Member<T> whenNotNull(Function<@Nullable T, ?> extractor) {
         Assert.notNull(extractor, "'extractor' must not be null");
         return this.when(instance -> Objects.nonNull(extractor.apply((T)instance)));
      }

      public JsonWriter.Member<T> whenHasLength() {
         return this.when(instance -> instance != null && StringUtils.hasLength(instance.toString()));
      }

      public JsonWriter.Member<T> whenNotEmpty() {
         Predicate<T> isEmpty = ObjectUtils::isEmpty;
         return this.whenNot(isEmpty);
      }

      public JsonWriter.Member<T> whenNot(Predicate<@Nullable T> predicate) {
         Assert.notNull(predicate, "'predicate' must not be null");
         return this.when(predicate.negate());
      }

      public JsonWriter.Member<T> when(Predicate<? super @Nullable T> predicate) {
         Assert.notNull(predicate, "'predicate' must not be null");
         this.valueExtractor = this.valueExtractor.when(predicate);
         return this;
      }

      public <R> JsonWriter.Member<R> as(JsonWriter.Extractor<T, R> extractor) {
         Assert.notNull(extractor, "'extractor' must not be null");
         JsonWriter.Member<R> result = (JsonWriter.Member<R>)this;
         result.valueExtractor = this.valueExtractor.as(extractor::extract);
         return result;
      }

      public <E> JsonWriter.Member<T> usingExtractedPairs(BiConsumer<T, Consumer<E>> elements, JsonWriter.PairExtractor<E> extractor) {
         Assert.notNull(elements, "'elements' must not be null");
         Assert.notNull(extractor, "'extractor' must not be null");
         return this.usingExtractedPairs(elements, extractor::getName, extractor::getValue);
      }

      public <E, N, V> JsonWriter.Member<T> usingExtractedPairs(
         BiConsumer<T, Consumer<E>> elements, Function<E, N> nameExtractor, Function<E, V> valueExtractor
      ) {
         Assert.notNull(elements, "'elements' must not be null");
         Assert.notNull(nameExtractor, "'nameExtractor' must not be null");
         Assert.notNull(valueExtractor, "'valueExtractor' must not be null");
         return this.usingPairs((instance, pairsConsumer) -> elements.accept(instance, element -> {
            N name = nameExtractor.apply(element);
            V value = valueExtractor.apply(element);
            pairsConsumer.accept(name, value);
         }));
      }

      public <N, V> JsonWriter.Member<T> usingPairs(BiConsumer<T, BiConsumer<N, V>> pairs) {
         Assert.notNull(pairs, "'pairs' must not be null");
         Assert.state(this.pairs == null, "Pairs cannot be declared multiple times");
         Assert.state(this.members == null, "Pairs cannot be declared when using members");
         this.pairs = pairs;
         return this;
      }

      public JsonWriter.Member<T> usingMembers(Consumer<JsonWriter.Members<T>> members) {
         Assert.notNull(members, "'members' must not be null");
         Assert.state(this.members == null, "Members cannot be declared multiple times");
         Assert.state(this.pairs == null, "Members cannot be declared when using pairs");
         this.members = new JsonWriter.Members<>(members, this.name == null);
         return this;
      }

      void write(@Nullable Object instance, JsonValueWriter valueWriter) {
         T extracted = (T)this.valueExtractor.extract(instance);
         if (!JsonWriter.Member.ValueExtractor.skip(extracted)) {
            Object value = this.getValueToWrite(extracted, valueWriter);
            valueWriter.write(this.name, value);
         }
      }

      private @Nullable Object getValueToWrite(@Nullable T extracted, JsonValueWriter valueWriter) {
         WritableJson writableJson = this.getWritableJsonToWrite(extracted, valueWriter);
         return writableJson != null ? WritableJson.of(writableJson) : extracted;
      }

      private @Nullable WritableJson getWritableJsonToWrite(@Nullable T extracted, JsonValueWriter valueWriter) {
         BiConsumer<T, BiConsumer<?, ?>> pairs = this.pairs;
         if (pairs != null) {
            return out -> valueWriter.writePairs(outPairs -> pairs.accept(extracted, outPairs));
         }

         JsonWriter.Members<T> members = this.members;
         return members != null ? out -> members.write(extracted, valueWriter) : null;
      }

      boolean contributesPair() {
         return this.name != null || this.pairs != null || this.members != null && this.members.contributesPair();
      }

      @Override
      public String toString() {
         return "Member at index " + this.index + (this.name != null ? "{%s}".formatted(this.name) : "");
      }

      @FunctionalInterface
      interface ValueExtractor<T> {
         Object SKIP = new Object();

         @Nullable T extract(@Nullable Object instance);

         default JsonWriter.Member.ValueExtractor<T> when(Predicate<? super @Nullable T> predicate) {
            return instance -> this.test(this.extract(instance), predicate);
         }

         private @Nullable T test(@Nullable T extracted, Predicate<? super @Nullable T> predicate) {
            return (T)(!skip(extracted) && predicate.test(extracted) ? extracted : SKIP);
         }

         default <R> JsonWriter.Member.ValueExtractor<R> as(JsonWriter.Extractor<T, R> extractor) {
            return instance -> this.apply(this.extract(instance), extractor);
         }

         private <R> @Nullable R apply(@Nullable T value, JsonWriter.Extractor<T, R> extractor) {
            if (skip(value)) {
               return (R)SKIP;
            } else {
               return value != null ? extractor.extract(value) : null;
            }
         }

         static <S, T> JsonWriter.Member.ValueExtractor<T> of(JsonWriter.Extractor<S, T> extractor) {
            return instance -> {
               if (instance == null) {
                  return null;
               } else {
                  return (T)(skip(instance) ? SKIP : extractor.extract((S)instance));
               }
            };
         }

         static <T> boolean skip(@Nullable T extracted) {
            return extracted == SKIP;
         }
      }
   }

   record MemberPath(JsonWriter.@Nullable MemberPath parent, @Nullable String name, int index) {
      private static final String[] ESCAPED = new String[]{"\\", ".", "[", "]"};
      public static final int UNINDEXED = -1;
      static final JsonWriter.MemberPath ROOT = new JsonWriter.MemberPath(null, "", -1);

      public MemberPath {
         Assert.isTrue(name != null && index < 0 || name == null && index >= 0, "'name' and 'index' cannot be mixed");
      }

      public JsonWriter.MemberPath child(int index) {
         return new JsonWriter.MemberPath(this, null, index);
      }

      public JsonWriter.MemberPath child(String name) {
         return !StringUtils.hasLength(name) ? this : new JsonWriter.MemberPath(this, name, -1);
      }

      @Override
      public String toString() {
         return this.toString(true);
      }

      public String toUnescapedString() {
         return this.toString(false);
      }

      private String toString(boolean escape) {
         StringBuilder string = new StringBuilder(this.parent != null ? this.parent.toString(escape) : "");
         if (this.index >= 0) {
            string.append("[").append(this.index).append("]");
         } else {
            string.append(!string.isEmpty() ? "." : "").append(!escape ? this.name : this.escape(this.name));
         }

         return string.toString();
      }

      private @Nullable String escape(@Nullable String name) {
         if (name == null) {
            return null;
         }

         for (String escape : ESCAPED) {
            name = name.replace(escape, "\\" + escape);
         }

         return name;
      }

      public static JsonWriter.MemberPath of(String value) {
         JsonWriter.MemberPath path = ROOT;
         StringBuilder buffer = new StringBuilder();
         boolean escape = false;

         for (char ch : value.toCharArray()) {
            if (!escape && ch == '\\') {
               escape = true;
            } else if (escape || ch != '.' && ch != '[') {
               if (!escape && ch == ']') {
                  path = path.child(Integer.parseUnsignedInt(buffer.toString()));
                  buffer.setLength(0);
               } else {
                  buffer.append(ch);
                  escape = false;
               }
            } else {
               path = path.child(buffer.toString());
               buffer.setLength(0);
            }
         }

         return path.child(buffer.toString());
      }
   }

   final class Members<T> {
      private final List<JsonWriter.Member<?>> members = new ArrayList<>();
      private final boolean contributesPair;
      private final JsonValueWriter.@Nullable Series series;
      private final JsonWriterFiltersAndProcessors jsonProcessors = new JsonWriterFiltersAndProcessors();

      Members(Consumer<JsonWriter.Members<T>> members, boolean contributesToExistingSeries) {
         Assert.notNull(members, "'members' must not be null");
         members.accept(this);
         Assert.state(!this.members.isEmpty(), "No members have been added");
         this.contributesPair = this.members.stream().anyMatch(JsonWriter.Member::contributesPair);
         this.series = this.contributesPair && !contributesToExistingSeries ? JsonValueWriter.Series.OBJECT : null;
         if (this.contributesPair || this.members.size() > 1) {
            this.members
               .forEach(
                  member -> Assert.state(
                     member.contributesPair(),
                     () -> String.format(
                        "%s does not contribute a named pair, ensure that all members have a name or call an appropriate 'using' method", member
                     )
                  )
               );
         }
      }

      public JsonWriter.Member<T> add(String name) {
         return this.add(name, instance -> instance);
      }

      public <V> JsonWriter.Member<V> add(String name, @Nullable V value) {
         return this.add(name, instance -> value);
      }

      public <V> JsonWriter.Member<V> add(String name, Supplier<@Nullable V> supplier) {
         Assert.notNull(supplier, "'supplier' must not be null");
         return this.add(name, instance -> supplier.get());
      }

      public <V> JsonWriter.Member<V> add(String name, JsonWriter.Extractor<T, V> extractor) {
         Assert.notNull(name, "'name' must not be null");
         Assert.notNull(extractor, "'extractor' must not be null");
         return this.addMember(name, extractor);
      }

      public JsonWriter.Member<T> add() {
         return this.from(value -> value);
      }

      public <M extends Map<K, V>, K, V> JsonWriter.Member<M> addMapEntries(JsonWriter.Extractor<T, M> extractor) {
         return this.from(extractor).usingPairs(Map::forEach);
      }

      public <V> JsonWriter.Member<V> from(@Nullable V value) {
         return this.from(instance -> value);
      }

      public <V> JsonWriter.Member<V> from(Supplier<@Nullable V> supplier) {
         Assert.notNull(supplier, "'supplier' must not be null");
         return this.from(instance -> supplier.get());
      }

      public <V> JsonWriter.Member<V> from(JsonWriter.Extractor<T, V> extractor) {
         Assert.notNull(extractor, "'extractor' must not be null");
         return this.addMember(null, extractor);
      }

      public void applyingPathFilter(Predicate<JsonWriter.MemberPath> predicate) {
         Assert.notNull(predicate, "'predicate' must not be null");
         this.jsonProcessors.pathFilters().add(predicate);
      }

      public void applyingNameProcessor(JsonWriter.NameProcessor nameProcessor) {
         Assert.notNull(nameProcessor, "'nameProcessor' must not be null");
         this.jsonProcessors.nameProcessors().add(nameProcessor);
      }

      public void applyingValueProcessor(JsonWriter.ValueProcessor<?> valueProcessor) {
         Assert.notNull(valueProcessor, "'valueProcessor' must not be null");
         this.jsonProcessors.valueProcessors().add(valueProcessor);
      }

      private <V> JsonWriter.Member<V> addMember(@Nullable String name, JsonWriter.Extractor<T, V> extractor) {
         JsonWriter.Member<V> member = new JsonWriter.Member<>(this.members.size(), name, JsonWriter.Member.ValueExtractor.of(extractor));
         this.members.add(member);
         return member;
      }

      void write(@Nullable T instance, JsonValueWriter valueWriter) {
         valueWriter.pushProcessors(this.jsonProcessors);
         valueWriter.start(this.series);

         for (JsonWriter.Member<?> member : this.members) {
            member.write(instance, valueWriter);
         }

         valueWriter.end(this.series);
         valueWriter.popProcessors();
      }

      boolean contributesPair() {
         return this.contributesPair;
      }
   }

   @FunctionalInterface
   interface NameProcessor {
      @Nullable String processName(JsonWriter.MemberPath path, String existingName);

      static JsonWriter.NameProcessor of(UnaryOperator<String> operation) {
         Assert.notNull(operation, "'operation' must not be null");
         return (path, existingName) -> operation.apply(existingName);
      }
   }

   interface PairExtractor<E> {
      <N> N getName(E element);

      <V> V getValue(E element);

      static <T> JsonWriter.PairExtractor<T> of(Function<T, ?> nameExtractor, Function<T, ?> valueExtractor) {
         Assert.notNull(nameExtractor, "'nameExtractor' must not be null");
         Assert.notNull(valueExtractor, "'valueExtractor' must not be null");
         return new JsonWriter.PairExtractor<T>() {
            @Override
            public <N> N getName(T instance) {
               return (N)nameExtractor.apply(instance);
            }

            @Override
            public <V> V getValue(T instance) {
               return (V)valueExtractor.apply(instance);
            }
         };
      }
   }

   @FunctionalInterface
   interface ValueProcessor<T> {
      @Nullable T processValue(JsonWriter.MemberPath path, @Nullable T value);

      default JsonWriter.ValueProcessor<T> whenHasUnescapedPath(String path) {
         return this.whenHasPath(candidate -> candidate.toString(false).equals(path));
      }

      default JsonWriter.ValueProcessor<T> whenHasPath(String path) {
         return this.whenHasPath(JsonWriter.MemberPath.of(path)::equals);
      }

      default JsonWriter.ValueProcessor<T> whenHasPath(Predicate<JsonWriter.MemberPath> predicate) {
         return (path, value) -> predicate.test(path) ? this.processValue(path, value) : value;
      }

      default JsonWriter.ValueProcessor<T> whenInstanceOf(Class<?> type) {
         Predicate<T> isInstance = type::isInstance;
         return this.when(isInstance);
      }

      default JsonWriter.ValueProcessor<T> when(Predicate<@Nullable T> predicate) {
         return (name, value) -> predicate.test(value) ? this.processValue(name, value) : value;
      }

      static <T> JsonWriter.ValueProcessor<T> of(Class<? extends T> type, UnaryOperator<@Nullable T> action) {
         return of(action).whenInstanceOf(type);
      }

      static <T> JsonWriter.ValueProcessor<T> of(UnaryOperator<@Nullable T> action) {
         Assert.notNull(action, "'action' must not be null");
         return (name, value) -> action.apply(value);
      }
   }
}
