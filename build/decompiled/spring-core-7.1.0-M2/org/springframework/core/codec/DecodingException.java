package org.springframework.core.codec;

import org.jspecify.annotations.Nullable;

public class DecodingException extends CodecException {
   public DecodingException(@Nullable String msg) {
      super(msg);
   }

   public DecodingException(@Nullable String msg, @Nullable Throwable cause) {
      super(msg, cause);
   }
}
