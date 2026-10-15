package org.springframework.util;

import org.jspecify.annotations.Nullable;

public class InvalidMimeTypeException extends IllegalArgumentException {
   private final String mimeType;

   public InvalidMimeTypeException(String mimeType, @Nullable String message) {
      super(message == null ? "Invalid mime type \"" + mimeType + "\"" : "Invalid mime type \"" + mimeType + "\": " + message);
      this.mimeType = mimeType;
   }

   public String getMimeType() {
      return this.mimeType;
   }
}
