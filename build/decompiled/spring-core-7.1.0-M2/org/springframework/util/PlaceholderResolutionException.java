package org.springframework.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

public class PlaceholderResolutionException extends IllegalArgumentException {
   private final String reason;
   private final String placeholder;
   private final List<String> values;

   PlaceholderResolutionException(String reason, String placeholder, @Nullable String value) {
      this(reason, placeholder, value != null ? List.of(value) : Collections.emptyList());
   }

   private PlaceholderResolutionException(String reason, String placeholder, List<String> values) {
      super(buildMessage(reason, values));
      this.reason = reason;
      this.placeholder = placeholder;
      this.values = values;
   }

   private static String buildMessage(String reason, List<String> values) {
      StringBuilder sb = new StringBuilder();
      sb.append(reason);
      if (!CollectionUtils.isEmpty(values)) {
         String valuesChain = values.stream().map(value -> "\"" + value + "\"").collect(Collectors.joining(" <-- "));
         sb.append(" in value %s".formatted(valuesChain));
      }

      return sb.toString();
   }

   PlaceholderResolutionException withValue(String value) {
      List<String> allValues = new ArrayList<>(this.values);
      allValues.add(value);
      return new PlaceholderResolutionException(this.reason, this.placeholder, allValues);
   }

   public String getPlaceholder() {
      return this.placeholder;
   }

   public List<String> getValues() {
      return this.values;
   }
}
