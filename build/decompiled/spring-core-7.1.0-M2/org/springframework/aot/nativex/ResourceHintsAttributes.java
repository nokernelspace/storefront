package org.springframework.aot.nativex;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.aot.hint.ConditionalHint;
import org.springframework.aot.hint.ResourceBundleHint;
import org.springframework.aot.hint.ResourceHints;
import org.springframework.aot.hint.ResourcePatternHint;
import org.springframework.aot.hint.ResourcePatternHints;

class ResourceHintsAttributes {
   private static final Comparator<ResourcePatternHint> RESOURCE_PATTERN_HINT_COMPARATOR = Comparator.comparing(ResourcePatternHint::getPattern);
   private static final Comparator<ResourceBundleHint> RESOURCE_BUNDLE_HINT_COMPARATOR = Comparator.comparing(ResourceBundleHint::getBaseName);

   public List<Map<String, Object>> resources(ResourceHints hint) {
      List<Map<String, Object>> resourceHints = new ArrayList<>();
      resourceHints.addAll(
         hint.resourcePatternHints()
            .map(ResourcePatternHints::getIncludes)
            .flatMap(Collection::stream)
            .distinct()
            .sorted(RESOURCE_PATTERN_HINT_COMPARATOR)
            .map(this::toAttributes)
            .toList()
      );
      resourceHints.addAll(hint.resourceBundleHints().sorted(RESOURCE_BUNDLE_HINT_COMPARATOR).map(this::toAttributes).toList());
      return resourceHints;
   }

   private Map<String, Object> toAttributes(ResourceBundleHint hint) {
      Map<String, Object> attributes = new LinkedHashMap<>();
      this.handleCondition(attributes, hint);
      attributes.put("bundle", hint.getBaseName());
      return attributes;
   }

   private Map<String, Object> toAttributes(ResourcePatternHint hint) {
      Map<String, Object> attributes = new LinkedHashMap<>();
      this.handleCondition(attributes, hint);
      attributes.put("glob", hint.getPattern());
      return attributes;
   }

   private void handleCondition(Map<String, Object> attributes, ConditionalHint hint) {
      if (hint.getReachableType() != null) {
         Map<String, Object> conditionAttributes = new LinkedHashMap<>();
         conditionAttributes.put("typeReached", hint.getReachableType());
         attributes.put("condition", conditionAttributes);
      }
   }
}
