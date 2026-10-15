package org.springframework.beans.factory.aot;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.support.RegisteredBean;

@FunctionalInterface
public interface BeanRegistrationAotProcessor {
   String IGNORE_REGISTRATION_ATTRIBUTE = "aotProcessingIgnoreRegistration";

   @Nullable BeanRegistrationAotContribution processAheadOfTime(RegisteredBean registeredBean);

   default boolean isBeanExcludedFromAotProcessing() {
      return true;
   }
}
