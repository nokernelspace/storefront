package org.springframework.boot.web.error;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.validation.ObjectError;

public final class Error implements MessageSourceResolvable {
   private final MessageSourceResolvable cause;

   private Error(MessageSourceResolvable cause) {
      Assert.notNull(cause, "'cause' must not be null");
      this.cause = cause;
   }

   public String @Nullable [] getCodes() {
      return this.cause.getCodes();
   }

   public Object @Nullable [] getArguments() {
      return this.cause.getArguments();
   }

   public @Nullable String getDefaultMessage() {
      return this.cause.getDefaultMessage();
   }

   @JsonIgnore
   public MessageSourceResolvable getCause() {
      return this.cause;
   }

   @Override
   public boolean equals(Object obj) {
      if (this == obj) {
         return true;
      } else {
         return obj != null && this.getClass() == obj.getClass() ? Objects.equals(this.cause, ((Error)obj).cause) : false;
      }
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.cause);
   }

   @Override
   public String toString() {
      return this.cause.toString();
   }

   public static List<MessageSourceResolvable> wrapIfNecessary(List<? extends MessageSourceResolvable> errors) {
      if (CollectionUtils.isEmpty(errors)) {
         return Collections.emptyList();
      }

      List<MessageSourceResolvable> result = new ArrayList<>(errors.size());

      for (MessageSourceResolvable error : errors) {
         result.add(requiresWrapping(error) ? new Error(error) : error);
      }

      return List.copyOf(result);
   }

   private static boolean requiresWrapping(MessageSourceResolvable error) {
      return !(error instanceof ObjectError);
   }
}
