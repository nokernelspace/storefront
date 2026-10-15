package org.springframework.context;

import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface MessageSourceResolvable {
   String @Nullable [] getCodes();

   default Object @Nullable [] getArguments() {
      return null;
   }

   default @Nullable String getDefaultMessage() {
      return null;
   }
}
