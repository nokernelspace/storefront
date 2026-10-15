package org.springframework.boot;

import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

public interface ApplicationArguments {
   String[] getSourceArgs();

   Set<String> getOptionNames();

   boolean containsOption(String name);

   @Nullable List<String> getOptionValues(String name);

   List<String> getNonOptionArgs();
}
