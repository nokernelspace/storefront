package org.springframework.aot.generate;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Map.Entry;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.javapoet.CodeBlock;
import org.springframework.util.ClassUtils;
import org.springframework.util.ObjectUtils;

public abstract class ValueCodeGeneratorDelegates {
   public static final List<ValueCodeGenerator.Delegate> INSTANCES = List.of(
      new ValueCodeGeneratorDelegates.PrimitiveDelegate(),
      new ValueCodeGeneratorDelegates.StringDelegate(),
      new ValueCodeGeneratorDelegates.CharsetDelegate(),
      new ValueCodeGeneratorDelegates.EnumDelegate(),
      new ValueCodeGeneratorDelegates.ClassDelegate(),
      new ValueCodeGeneratorDelegates.ResolvableTypeDelegate(),
      new ValueCodeGeneratorDelegates.ArrayDelegate(),
      new ValueCodeGeneratorDelegates.ListDelegate(),
      new ValueCodeGeneratorDelegates.SetDelegate(),
      new ValueCodeGeneratorDelegates.MapDelegate()
   );

   private static class ArrayDelegate implements ValueCodeGenerator.Delegate {
      @Override
      public @Nullable CodeBlock generateCode(ValueCodeGenerator codeGenerator, Object value) {
         if (value.getClass().isArray()) {
            Stream<CodeBlock> elements = Arrays.stream(ObjectUtils.toObjectArray(value)).map(codeGenerator::generateCode);
            CodeBlock.Builder code = CodeBlock.builder();
            code.add("new $T {", value.getClass());
            code.add(elements.collect(CodeBlock.joining(", ")));
            code.add("}");
            return code.build();
         } else {
            return null;
         }
      }
   }

   private static class CharsetDelegate implements ValueCodeGenerator.Delegate {
      @Override
      public @Nullable CodeBlock generateCode(ValueCodeGenerator codeGenerator, Object value) {
         return value instanceof Charset charset ? CodeBlock.of("$T.forName($S)", Charset.class, charset.name()) : null;
      }
   }

   private static class ClassDelegate implements ValueCodeGenerator.Delegate {
      @Override
      public @Nullable CodeBlock generateCode(ValueCodeGenerator codeGenerator, Object value) {
         return value instanceof Class<?> clazz ? CodeBlock.of("$T.class", ClassUtils.getUserClass(clazz)) : null;
      }
   }

   public abstract static class CollectionDelegate<T extends Collection<?>> implements ValueCodeGenerator.Delegate {
      private final Class<?> collectionType;
      private final CodeBlock emptyResult;

      protected CollectionDelegate(Class<?> collectionType, CodeBlock emptyResult) {
         this.collectionType = collectionType;
         this.emptyResult = emptyResult;
      }

      @Override
      public @Nullable CodeBlock generateCode(ValueCodeGenerator valueCodeGenerator, Object value) {
         if (this.collectionType.isInstance(value)) {
            T collection = (T)value;
            return collection.isEmpty() ? this.emptyResult : this.generateCollectionCode(valueCodeGenerator, collection);
         } else {
            return null;
         }
      }

      protected CodeBlock generateCollectionCode(ValueCodeGenerator valueCodeGenerator, T collection) {
         return this.generateCollectionOf(valueCodeGenerator, collection, this.collectionType);
      }

      protected final CodeBlock generateCollectionOf(ValueCodeGenerator valueCodeGenerator, Collection<?> collection, Class<?> collectionType) {
         CodeBlock.Builder code = CodeBlock.builder();
         code.add("$T.of(", collectionType);
         Iterator<?> iterator = collection.iterator();

         while (iterator.hasNext()) {
            Object element = iterator.next();
            code.add("$L", valueCodeGenerator.generateCode(element));
            if (iterator.hasNext()) {
               code.add(", ");
            }
         }

         code.add(")");
         return code.build();
      }
   }

   private static class EnumDelegate implements ValueCodeGenerator.Delegate {
      @Override
      public @Nullable CodeBlock generateCode(ValueCodeGenerator codeGenerator, Object value) {
         return value instanceof Enum<?> enumValue ? CodeBlock.of("$T.$L", enumValue.getDeclaringClass(), enumValue.name()) : null;
      }
   }

   private static class ListDelegate extends ValueCodeGeneratorDelegates.CollectionDelegate<List<?>> {
      ListDelegate() {
         super(List.class, CodeBlock.of("$T.emptyList()", Collections.class));
      }
   }

   public static class MapDelegate implements ValueCodeGenerator.Delegate {
      private static final CodeBlock EMPTY_RESULT = CodeBlock.of("$T.emptyMap()", Collections.class);

      @Override
      public @Nullable CodeBlock generateCode(ValueCodeGenerator valueCodeGenerator, Object value) {
         if (value instanceof Map<?, ?> map) {
            return map.isEmpty() ? EMPTY_RESULT : this.generateMapCode(valueCodeGenerator, map);
         } else {
            return null;
         }
      }

      protected @Nullable CodeBlock generateMapCode(ValueCodeGenerator valueCodeGenerator, Map<?, ?> map) {
         map = this.orderForCodeConsistency(map);
         boolean useOfEntries = map.size() > 10;
         CodeBlock.Builder code = CodeBlock.builder();
         code.add("$T" + (!useOfEntries ? ".of(" : ".ofEntries("), Map.class);
         Iterator<? extends Entry<?, ?>> iterator = map.entrySet().iterator();

         while (iterator.hasNext()) {
            Entry<?, ?> entry = (Entry<?, ?>)iterator.next();
            CodeBlock keyCode = valueCodeGenerator.generateCode(entry.getKey());
            CodeBlock valueCode = valueCodeGenerator.generateCode(entry.getValue());
            if (!useOfEntries) {
               code.add("$L, $L", keyCode, valueCode);
            } else {
               code.add("$T.entry($L,$L)", Map.class, keyCode, valueCode);
            }

            if (iterator.hasNext()) {
               code.add(", ");
            }
         }

         code.add(")");
         return code.build();
      }

      private <K, V> Map<K, V> orderForCodeConsistency(Map<K, V> map) {
         try {
            return new TreeMap<>(map);
         } catch (ClassCastException ex) {
            return map;
         }
      }
   }

   private static class PrimitiveDelegate implements ValueCodeGenerator.Delegate {
      private static final Map<Character, String> CHAR_ESCAPES = Map.of(
         '\b', "\\b", '\t', "\\t", '\n', "\\n", '\f', "\\f", '\r', "\\r", '"', "\"", '\'', "\\'", '\\', "\\\\"
      );

      @Override
      public @Nullable CodeBlock generateCode(ValueCodeGenerator codeGenerator, Object value) {
         if (value instanceof Boolean || value instanceof Integer) {
            return CodeBlock.of("$L", value);
         }

         if (value instanceof Byte) {
            return CodeBlock.of("(byte) $L", value);
         }

         if (value instanceof Short) {
            return CodeBlock.of("(short) $L", value);
         }

         if (value instanceof Long) {
            return CodeBlock.of("$LL", value);
         }

         if (value instanceof Float floatValue) {
            if (Float.isNaN(floatValue)) {
               return CodeBlock.of("$T.NaN", Float.class);
            } else if (floatValue == Float.POSITIVE_INFINITY) {
               return CodeBlock.of("$T.POSITIVE_INFINITY", Float.class);
            } else {
               return floatValue == Float.NEGATIVE_INFINITY ? CodeBlock.of("$T.NEGATIVE_INFINITY", Float.class) : CodeBlock.of("$LF", value);
            }
         } else if (value instanceof Double doubleValue) {
            if (Double.isNaN(doubleValue)) {
               return CodeBlock.of("$T.NaN", Double.class);
            } else if (doubleValue == Double.POSITIVE_INFINITY) {
               return CodeBlock.of("$T.POSITIVE_INFINITY", Double.class);
            } else {
               return doubleValue == Double.NEGATIVE_INFINITY ? CodeBlock.of("$T.NEGATIVE_INFINITY", Double.class) : CodeBlock.of("(double) $L", value);
            }
         } else {
            return value instanceof Character character ? CodeBlock.of("'$L'", this.escape(character)) : null;
         }
      }

      private String escape(char ch) {
         String escaped = CHAR_ESCAPES.get(ch);
         if (escaped != null) {
            return escaped;
         } else {
            return !Character.isISOControl(ch) ? Character.toString(ch) : String.format("\\u%04x", Integer.valueOf(ch));
         }
      }
   }

   private static class ResolvableTypeDelegate implements ValueCodeGenerator.Delegate {
      @Override
      public @Nullable CodeBlock generateCode(ValueCodeGenerator codeGenerator, Object value) {
         return value instanceof ResolvableType resolvableType ? generateCode(resolvableType, false) : null;
      }

      private static CodeBlock generateCode(ResolvableType resolvableType, boolean allowClassResult) {
         if (ResolvableType.NONE.equals(resolvableType)) {
            return CodeBlock.of("$T.NONE", ResolvableType.class);
         } else {
            Class<?> type = ClassUtils.getUserClass(resolvableType.toClass());
            if (resolvableType.hasGenerics() && resolvableType.hasResolvableGenerics()) {
               return generateCodeWithGenerics(resolvableType, type);
            } else {
               return allowClassResult ? CodeBlock.of("$T.class", type) : CodeBlock.of("$T.forClass($T.class)", ResolvableType.class, type);
            }
         }
      }

      private static CodeBlock generateCodeWithGenerics(ResolvableType target, Class<?> type) {
         ResolvableType[] generics = target.getGenerics();
         boolean hasNoNestedGenerics = Arrays.stream(generics).noneMatch(ResolvableType::hasGenerics);
         CodeBlock.Builder code = CodeBlock.builder();
         code.add("$T.forClassWithGenerics($T.class", ResolvableType.class, type);

         for (ResolvableType generic : generics) {
            code.add(", $L", generateCode(generic, hasNoNestedGenerics));
         }

         code.add(")");
         return code.build();
      }
   }

   private static class SetDelegate extends ValueCodeGeneratorDelegates.CollectionDelegate<Set<?>> {
      SetDelegate() {
         super(Set.class, CodeBlock.of("$T.emptySet()", Collections.class));
      }

      protected CodeBlock generateCollectionCode(ValueCodeGenerator valueCodeGenerator, Set<?> collection) {
         return collection instanceof LinkedHashSet
            ? CodeBlock.of("new $T($L)", LinkedHashSet.class, this.generateCollectionOf(valueCodeGenerator, collection, List.class))
            : super.generateCollectionCode(valueCodeGenerator, this.orderForCodeConsistency(collection));
      }

      private Set<?> orderForCodeConsistency(Set<?> set) {
         try {
            return new TreeSet(set);
         } catch (ClassCastException ex) {
            return set;
         }
      }
   }

   private static class StringDelegate implements ValueCodeGenerator.Delegate {
      @Override
      public @Nullable CodeBlock generateCode(ValueCodeGenerator codeGenerator, Object value) {
         return value instanceof String ? CodeBlock.of("$S", value) : null;
      }
   }
}
