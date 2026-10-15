package org.springframework.scheduling.annotation;

import org.springframework.context.annotation.AdviceMode;
import org.springframework.context.annotation.AdviceModeImportSelector;

public class AsyncConfigurationSelector extends AdviceModeImportSelector<EnableAsync> {
   private static final String ASYNC_EXECUTION_ASPECT_CONFIGURATION_CLASS_NAME = "org.springframework.scheduling.aspectj.AspectJAsyncConfiguration";

   @Override
   public String[] selectImports(AdviceMode adviceMode) {
      return switch (adviceMode) {
         case PROXY -> new String[]{ProxyAsyncConfiguration.class.getName()};
         case ASPECTJ -> new String[]{"org.springframework.scheduling.aspectj.AspectJAsyncConfiguration"};
      };
   }
}
