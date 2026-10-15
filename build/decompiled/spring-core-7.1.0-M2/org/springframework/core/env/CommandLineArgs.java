package org.springframework.core.env;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jspecify.annotations.Nullable;

class CommandLineArgs {
   private final Map<String, List<String>> optionArgs = new HashMap<>();
   private final List<String> nonOptionArgs = new ArrayList<>();

   public void addOptionArg(String optionName, @Nullable String optionValue) {
      List<String> values = this.optionArgs.computeIfAbsent(optionName, key -> new ArrayList<>());
      if (optionValue != null) {
         values.add(optionValue);
      }
   }

   public Set<String> getOptionNames() {
      return Collections.unmodifiableSet(this.optionArgs.keySet());
   }

   public boolean containsOption(String optionName) {
      return this.optionArgs.containsKey(optionName);
   }

   public @Nullable List<String> getOptionValues(String optionName) {
      return this.optionArgs.get(optionName);
   }

   public void addNonOptionArg(String value) {
      this.nonOptionArgs.add(value);
   }

   public List<String> getNonOptionArgs() {
      return Collections.unmodifiableList(this.nonOptionArgs);
   }
}
