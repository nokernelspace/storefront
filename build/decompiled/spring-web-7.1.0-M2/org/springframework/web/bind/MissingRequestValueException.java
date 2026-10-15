package org.springframework.web.bind;

import org.jspecify.annotations.Nullable;

public class MissingRequestValueException extends ServletRequestBindingException {
   private final boolean missingAfterConversion;

   public MissingRequestValueException(String msg) {
      this(msg, false);
   }

   public MissingRequestValueException(String msg, boolean missingAfterConversion) {
      super(msg);
      this.missingAfterConversion = missingAfterConversion;
   }

   protected MissingRequestValueException(
      String msg, boolean missingAfterConversion, @Nullable String messageDetailCode, Object @Nullable [] messageDetailArguments
   ) {
      super(msg, messageDetailCode, messageDetailArguments);
      this.missingAfterConversion = missingAfterConversion;
   }

   public boolean isMissingAfterConversion() {
      return this.missingAfterConversion;
   }
}
