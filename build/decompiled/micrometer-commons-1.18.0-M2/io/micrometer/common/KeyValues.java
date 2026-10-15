package io.micrometer.common;

import io.micrometer.common.lang.internal.Contract;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.jspecify.annotations.Nullable;

public final class KeyValues implements Iterable<KeyValue> {
   private static final KeyValue[] EMPTY_KEY_VALUE_ARRAY = new KeyValue[0];
   private static final KeyValues EMPTY = new KeyValues(EMPTY_KEY_VALUE_ARRAY, 0);
   private final KeyValue[] sortedSet;
   private final int length;

   private KeyValues(KeyValue[] sortedSet, int length) {
      this.sortedSet = sortedSet;
      this.length = length;
   }

   private static boolean isSortedSet(KeyValue[] keyValues) {
      for (int i = 0; i < keyValues.length - 1; i++) {
         int cmp = keyValues[i].compareTo(keyValues[i + 1]);
         if (cmp >= 0) {
            return false;
         }
      }

      return true;
   }

   private static KeyValues toKeyValues(KeyValue[] keyValues) {
      if (!isSortedSet(keyValues)) {
         Arrays.sort(keyValues);
         int len = dedup(keyValues);
         return new KeyValues(keyValues, len);
      } else {
         return new KeyValues(keyValues, keyValues.length);
      }
   }

   private static int dedup(KeyValue[] keyValues) {
      int n = keyValues.length;
      if (n != 0 && n != 1) {
         int j = 0;

         for (int i = 0; i < n - 1; i++) {
            if (!keyValues[i].getKey().equals(keyValues[i + 1].getKey())) {
               keyValues[j++] = keyValues[i];
            }
         }

         keyValues[j++] = keyValues[n - 1];
         return j;
      } else {
         return n;
      }
   }

   private KeyValues merge(KeyValues other) {
      if (other.length == 0) {
         return this;
      }

      if (Objects.equals(this, other)) {
         return this;
      }

      KeyValue[] sortedSet = new KeyValue[this.length + other.length];
      int sortedIndex = 0;
      int thisIndex = 0;

      int otherIndex;
      for (otherIndex = 0; thisIndex < this.length && otherIndex < other.length; sortedIndex++) {
         KeyValue thisKeyValue = this.sortedSet[thisIndex];
         KeyValue otherKeyValue = other.sortedSet[otherIndex];
         int cmp = thisKeyValue.compareTo(otherKeyValue);
         if (cmp > 0) {
            sortedSet[sortedIndex] = otherKeyValue;
            otherIndex++;
         } else if (cmp < 0) {
            sortedSet[sortedIndex] = thisKeyValue;
            thisIndex++;
         } else {
            sortedSet[sortedIndex] = otherKeyValue;
            thisIndex++;
            otherIndex++;
         }
      }

      int thisRemaining = this.length - thisIndex;
      if (thisRemaining > 0) {
         System.arraycopy(this.sortedSet, thisIndex, sortedSet, sortedIndex, thisRemaining);
         sortedIndex += thisRemaining;
      } else {
         int otherRemaining = other.length - otherIndex;
         if (otherRemaining > 0) {
            System.arraycopy(other.sortedSet, otherIndex, sortedSet, sortedIndex, otherRemaining);
            sortedIndex += otherRemaining;
         }
      }

      return new KeyValues(sortedSet, sortedIndex);
   }

   public KeyValues and(String key, String value) {
      return this.and(KeyValue.of(key, value));
   }

   public KeyValues and(String @Nullable ... keyValues) {
      return isEmptyVarargs(keyValues) ? this : this.and(of(keyValues));
   }

   public KeyValues and(KeyValue @Nullable ... keyValues) {
      return isEmptyVarargs(keyValues) ? this : this.and(toKeyValues(keyValues));
   }

   public <E> KeyValues and(@Nullable Iterable<E> elements, Function<E, String> keyExtractor, Function<E, String> valueExtractor) {
      if (elements != null && elements.iterator().hasNext()) {
         Iterable<KeyValue> keyValues = () -> StreamSupport.stream(elements.spliterator(), false)
            .map(element -> KeyValue.of((E)element, keyExtractor, valueExtractor))
            .iterator();
         return this.and(keyValues);
      } else {
         return this;
      }
   }

   public KeyValues and(@Nullable Iterable<? extends KeyValue> keyValues) {
      if (keyValues == null || keyValues == EMPTY || !keyValues.iterator().hasNext()) {
         return this;
      } else {
         return this.length == 0 ? of(keyValues) : this.merge(of(keyValues));
      }
   }

   @Override
   public Iterator<KeyValue> iterator() {
      return new KeyValues.ArrayIterator();
   }

   @Override
   public Spliterator<KeyValue> spliterator() {
      return Spliterators.spliterator(this.sortedSet, 0, this.length, 1301);
   }

   public Stream<KeyValue> stream() {
      return StreamSupport.stream(this.spliterator(), false);
   }

   @Override
   public int hashCode() {
      int result = 1;

      for (int i = 0; i < this.length; i++) {
         result = 31 * result + this.sortedSet[i].hashCode();
      }

      return result;
   }

   @Override
   public boolean equals(@Nullable Object obj) {
      return this == obj || obj != null && this.getClass() == obj.getClass() && this.keyValuesEqual((KeyValues)obj);
   }

   private boolean keyValuesEqual(KeyValues obj) {
      if (this.sortedSet == obj.sortedSet) {
         return true;
      }

      if (this.length != obj.length) {
         return false;
      }

      for (int i = 0; i < this.length; i++) {
         if (!this.sortedSet[i].equals(obj.sortedSet[i])) {
            return false;
         }
      }

      return true;
   }

   public static KeyValues concat(@Nullable Iterable<? extends KeyValue> keyValues, @Nullable Iterable<? extends KeyValue> otherKeyValues) {
      return of(keyValues).and(otherKeyValues);
   }

   public static KeyValues concat(@Nullable Iterable<? extends KeyValue> keyValues, String @Nullable ... otherKeyValues) {
      return of(keyValues).and(otherKeyValues);
   }

   public static <E> KeyValues of(@Nullable Iterable<E> elements, Function<E, String> keyExtractor, Function<E, String> valueExtractor) {
      return empty().and(elements, keyExtractor, valueExtractor);
   }

   public static KeyValues of(@Nullable Iterable<? extends KeyValue> keyValues) {
      if (keyValues == null || keyValues == EMPTY || !keyValues.iterator().hasNext()) {
         return empty();
      } else if (keyValues instanceof KeyValues) {
         return (KeyValues)keyValues;
      } else if (keyValues instanceof Collection) {
         Collection<? extends KeyValue> keyValuesCollection = (Collection<? extends KeyValue>)keyValues;
         return toKeyValues(keyValuesCollection.toArray(EMPTY_KEY_VALUE_ARRAY));
      } else {
         return toKeyValues(StreamSupport.stream(keyValues.spliterator(), false).toArray(KeyValue[]::new));
      }
   }

   public static KeyValues of(String key, String value) {
      return new KeyValues(new KeyValue[]{KeyValue.of(key, value)}, 1);
   }

   public static KeyValues of(String @Nullable ... keyValues) {
      if (isEmptyVarargs(keyValues)) {
         return empty();
      }

      if (keyValues.length % 2 == 1) {
         throw new IllegalArgumentException("size must be even, it is a set of key=value pairs");
      }

      KeyValue[] keyValueArray = new KeyValue[keyValues.length / 2];

      for (int i = 0; i < keyValues.length; i += 2) {
         keyValueArray[i / 2] = KeyValue.of(keyValues[i], keyValues[i + 1]);
      }

      return toKeyValues(keyValueArray);
   }

   @Contract("null -> true")
   private static boolean isEmptyVarargs(@Nullable Object @Nullable [] args) {
      return args == null || args.length == 0 || args.length == 1 && args[0] == null;
   }

   public static KeyValues of(KeyValue @Nullable ... keyValues) {
      return empty().and(keyValues);
   }

   public static KeyValues empty() {
      return EMPTY;
   }

   @Override
   public String toString() {
      return this.stream().<CharSequence>map(KeyValue::toString).collect(Collectors.joining(",", "[", "]"));
   }

   private class ArrayIterator implements Iterator<KeyValue> {
      private int currentIndex = 0;

      private ArrayIterator() {
      }

      @Override
      public boolean hasNext() {
         return this.currentIndex < KeyValues.this.length;
      }

      public KeyValue next() {
         return KeyValues.this.sortedSet[this.currentIndex++];
      }

      @Override
      public void remove() {
         throw new UnsupportedOperationException("cannot remove items from key values");
      }
   }
}
