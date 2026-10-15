package org.springframework.boot.context.properties.source;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.lang.Contract;
import org.springframework.util.Assert;

public class MutuallyExclusiveConfigurationPropertiesException extends RuntimeException {
   private final Set<String> configuredNames;
   private final Set<String> mutuallyExclusiveNames;

   public MutuallyExclusiveConfigurationPropertiesException(Collection<String> configuredNames, Collection<String> mutuallyExclusiveNames) {
      this(asSet(configuredNames), asSet(mutuallyExclusiveNames));
   }

   private MutuallyExclusiveConfigurationPropertiesException(Set<String> configuredNames, Set<String> mutuallyExclusiveNames) {
      super(buildMessage(mutuallyExclusiveNames, configuredNames));
      this.configuredNames = configuredNames;
      this.mutuallyExclusiveNames = mutuallyExclusiveNames;
   }

   public Set<String> getConfiguredNames() {
      return this.configuredNames;
   }

   public Set<String> getMutuallyExclusiveNames() {
      return this.mutuallyExclusiveNames;
   }

   @Contract("null -> null; !null -> !null")
   private static @Nullable Set<String> asSet(@Nullable Collection<String> collection) {
      return collection != null ? new LinkedHashSet<>(collection) : null;
   }

   private static String buildMessage(Set<String> mutuallyExclusiveNames, Set<String> configuredNames) {
      Assert.isTrue(configuredNames != null && configuredNames.size() > 1, "'configuredNames' must contain 2 or more names");
      Assert.isTrue(mutuallyExclusiveNames != null && mutuallyExclusiveNames.size() > 1, "'mutuallyExclusiveNames' must contain 2 or more names");
      return "The configuration properties '"
         + String.join(", ", mutuallyExclusiveNames)
         + "' are mutually exclusive and '"
         + String.join(", ", configuredNames)
         + "' have been configured together";
   }

   public static void throwIfMultipleNonNullValuesIn(Consumer<Map<String, Object>> entries) {
      Predicate<Object> isNonNull = Objects::nonNull;
      throwIfMultipleMatchingValuesIn(entries, isNonNull);
   }

   public static <V> void throwIfMultipleMatchingValuesIn(Consumer<Map<String, @Nullable V>> entries, Predicate<@Nullable V> predicate) {
      Map<String, V> map = new LinkedHashMap<>();
      entries.accept(map);
      Set<String> configuredNames = map.entrySet()
         .stream()
         .filter(entry -> predicate.test(entry.getValue()))
         .map(Entry::getKey)
         .collect(Collectors.toCollection(LinkedHashSet::new));
      if (configuredNames.size() > 1) {
         throw new MutuallyExclusiveConfigurationPropertiesException(configuredNames, map.keySet());
      }
   }
}
