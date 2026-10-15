package org.springframework.core.codec;

import org.jspecify.annotations.Nullable;
import org.springframework.core.NestedRuntimeException;

public class CodecException extends NestedRuntimeException {
   public CodecException(@Nullable String msg) {
      super(msg);
   }

   public CodecException(@Nullable String msg, @Nullable Throwable cause) {
      super(msg, cause);
   }
}
