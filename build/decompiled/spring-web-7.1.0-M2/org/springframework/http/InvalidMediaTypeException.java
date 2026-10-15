package org.springframework.http;

import org.jspecify.annotations.Nullable;
import org.springframework.util.InvalidMimeTypeException;

public class InvalidMediaTypeException extends IllegalArgumentException {
   private final String mediaType;

   public InvalidMediaTypeException(String mediaType, @Nullable String message) {
      super(message != null ? "Invalid media type \"" + mediaType + "\": " + message : "Invalid media type \"" + mediaType);
      this.mediaType = mediaType;
   }

   InvalidMediaTypeException(InvalidMimeTypeException ex) {
      super(ex.getMessage(), ex);
      this.mediaType = ex.getMimeType();
   }

   @Override
   public String getMessage() {
      return super.getMessage();
   }

   public String getMediaType() {
      return this.mediaType;
   }
}
