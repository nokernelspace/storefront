package org.springframework.boot;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface SpringApplicationHook {
   @Nullable SpringApplicationRunListener getRunListener(SpringApplication springApplication);
}
