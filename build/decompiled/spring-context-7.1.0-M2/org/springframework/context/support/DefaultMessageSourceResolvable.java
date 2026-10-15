package org.springframework.context.support;

import java.io.Serializable;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public class DefaultMessageSourceResolvable implements MessageSourceResolvable, Serializable {
   private final String @Nullable [] codes;
   private final Object @Nullable [] arguments;
   private final @Nullable String defaultMessage;

   public DefaultMessageSourceResolvable(String code) {
      this(new String[]{code}, null, null);
   }

   public DefaultMessageSourceResolvable(String[] codes) {
      this(codes, null, null);
   }

   public DefaultMessageSourceResolvable(String[] codes, String defaultMessage) {
      this(codes, null, defaultMessage);
   }

   public DefaultMessageSourceResolvable(String[] codes, Object[] arguments) {
      this(codes, arguments, null);
   }

   public DefaultMessageSourceResolvable(String @Nullable [] codes, Object @Nullable [] arguments, @Nullable String defaultMessage) {
      this.codes = codes;
      this.arguments = arguments;
      this.defaultMessage = defaultMessage;
   }

   public DefaultMessageSourceResolvable(MessageSourceResolvable resolvable) {
      this(resolvable.getCodes(), resolvable.getArguments(), resolvable.getDefaultMessage());
   }

   public @Nullable String getCode() {
      return this.codes != null && this.codes.length > 0 ? this.codes[this.codes.length - 1] : null;
   }

   @Override
   public String @Nullable [] getCodes() {
      return this.codes;
   }

   @Override
   public Object @Nullable [] getArguments() {
      return this.arguments;
   }

   @Override
   public @Nullable String getDefaultMessage() {
      return this.defaultMessage;
   }

   public boolean shouldRenderDefaultMessage() {
      return true;
   }

   protected final String resolvableToString() {
      StringBuilder result = new StringBuilder(64);
      result.append("codes [").append(StringUtils.arrayToDelimitedString(this.codes, ","));
      result.append("]; arguments [").append(StringUtils.arrayToDelimitedString(this.arguments, ","));
      result.append("]; default message [").append(this.defaultMessage).append(']');
      return result.toString();
   }

   @Override
   public String toString() {
      return this.getClass().getName() + ": " + this.resolvableToString();
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof MessageSourceResolvable that
            && ObjectUtils.nullSafeEquals(this.getCodes(), that.getCodes())
            && ObjectUtils.nullSafeEquals(this.getArguments(), that.getArguments())
            && ObjectUtils.nullSafeEquals(this.getDefaultMessage(), that.getDefaultMessage());
   }

   @Override
   public int hashCode() {
      return ObjectUtils.nullSafeHash(new Object[]{this.getCode(), this.getArguments(), this.getDefaultMessage()});
   }
}
