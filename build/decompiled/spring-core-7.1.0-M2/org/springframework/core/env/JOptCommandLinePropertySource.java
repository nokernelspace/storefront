package org.springframework.core.env;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.jspecify.annotations.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Deprecated(since = "6.1")
public class JOptCommandLinePropertySource extends CommandLinePropertySource<OptionSet> {
   public JOptCommandLinePropertySource(OptionSet options) {
      super(options);
   }

   public JOptCommandLinePropertySource(String name, OptionSet options) {
      super(name, options);
   }

   @Override
   protected boolean containsOption(String name) {
      return this.source.has(name);
   }

   @Override
   public String[] getPropertyNames() {
      List<String> names = new ArrayList<>();

      for (OptionSpec<?> spec : this.source.specs()) {
         String lastOption = CollectionUtils.lastElement(spec.options());
         if (lastOption != null) {
            names.add(lastOption);
         }
      }

      return StringUtils.toStringArray(names);
   }

   @Override
   public @Nullable List<String> getOptionValues(String name) {
      List<?> argValues = this.source.valuesOf(name);
      List<String> stringArgValues = new ArrayList<>();

      for (Object argValue : argValues) {
         stringArgValues.add(argValue.toString());
      }

      if (stringArgValues.isEmpty()) {
         return this.source.has(name) ? Collections.emptyList() : null;
      } else {
         return Collections.unmodifiableList(stringArgValues);
      }
   }

   @Override
   protected List<String> getNonOptionArgs() {
      List<?> argValues = this.source.nonOptionArguments();
      List<String> stringArgValues = new ArrayList<>();

      for (Object argValue : argValues) {
         stringArgValues.add(argValue.toString());
      }

      return stringArgValues.isEmpty() ? Collections.emptyList() : Collections.unmodifiableList(stringArgValues);
   }
}
