package org.springframework.core.env;

import java.util.Collection;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

public abstract class CommandLinePropertySource<T> extends EnumerablePropertySource<T> {
   public static final String COMMAND_LINE_PROPERTY_SOURCE_NAME = "commandLineArgs";
   public static final String DEFAULT_NON_OPTION_ARGS_PROPERTY_NAME = "nonOptionArgs";
   private String nonOptionArgsPropertyName = "nonOptionArgs";

   public CommandLinePropertySource(T source) {
      super("commandLineArgs", source);
   }

   public CommandLinePropertySource(String name, T source) {
      super(name, source);
   }

   public void setNonOptionArgsPropertyName(String nonOptionArgsPropertyName) {
      this.nonOptionArgsPropertyName = nonOptionArgsPropertyName;
   }

   @Override
   public final boolean containsProperty(String name) {
      return this.nonOptionArgsPropertyName.equals(name) ? !this.getNonOptionArgs().isEmpty() : this.containsOption(name);
   }

   public final @Nullable String getProperty(String name) {
      if (this.nonOptionArgsPropertyName.equals(name)) {
         Collection<String> nonOptionArguments = this.getNonOptionArgs();
         return nonOptionArguments.isEmpty() ? null : StringUtils.collectionToCommaDelimitedString(nonOptionArguments);
      } else {
         Collection<String> optionValues = this.getOptionValues(name);
         return optionValues == null ? null : StringUtils.collectionToCommaDelimitedString(optionValues);
      }
   }

   protected abstract boolean containsOption(String name);

   protected abstract @Nullable List<String> getOptionValues(String name);

   protected abstract List<String> getNonOptionArgs();
}
