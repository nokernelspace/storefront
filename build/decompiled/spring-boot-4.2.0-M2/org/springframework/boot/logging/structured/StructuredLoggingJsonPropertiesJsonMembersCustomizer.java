package org.springframework.boot.logging.structured;

import java.util.Map;
import org.springframework.boot.json.JsonWriter;
import org.springframework.boot.util.Instantiator;
import org.springframework.util.CollectionUtils;

class StructuredLoggingJsonPropertiesJsonMembersCustomizer implements StructuredLoggingJsonMembersCustomizer<Object> {
   private final Instantiator<?> instantiator;
   private final StructuredLoggingJsonProperties properties;
   private final boolean nested;

   StructuredLoggingJsonPropertiesJsonMembersCustomizer(Instantiator<?> instantiator, StructuredLoggingJsonProperties properties, boolean nested) {
      this.instantiator = instantiator;
      this.properties = properties;
      this.nested = nested;
   }

   @Override
   public void customize(JsonWriter.Members<Object> members) {
      members.applyingPathFilter(this::filterPath);
      members.applyingNameProcessor(this::renameJsonMembers);
      Map<String, String> add = this.properties.add();
      if (!CollectionUtils.isEmpty(add)) {
         if (this.nested) {
            ContextPairs contextPairs = new ContextPairs(true, "");
            members.add().usingPairs(contextPairs.nested(pairs -> pairs.addMapEntries(source -> add)));
         } else {
            add.forEach(members::add);
         }
      }

      this.properties.customizers(this.instantiator).forEach(customizer -> customizer.customize(members));
   }

   String renameJsonMembers(JsonWriter.MemberPath path, String existingName) {
      Map<String, String> rename = this.properties.rename();
      String key = path.toUnescapedString();
      return !CollectionUtils.isEmpty(rename) ? rename.getOrDefault(key, existingName) : existingName;
   }

   boolean filterPath(JsonWriter.MemberPath path) {
      boolean included = CollectionUtils.isEmpty(this.properties.include()) || this.properties.include().contains(path.toUnescapedString());
      boolean excluded = !CollectionUtils.isEmpty(this.properties.exclude()) && this.properties.exclude().contains(path.toUnescapedString());
      return !included || excluded;
   }
}
