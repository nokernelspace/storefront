package org.springframework.http.codec.protobuf;

import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;

public abstract class ProtobufCodecSupport {
   protected static final MimeType[] MIME_TYPES = new MimeType[]{
      new MimeType("application", "x-protobuf"),
      new MimeType("application", "*+x-protobuf"),
      new MimeType("application", "octet-stream"),
      new MimeType("application", "vnd.google.protobuf")
   };
   static final String DELIMITED_KEY = "delimited";
   static final String DELIMITED_VALUE = "true";
   private List<MimeType> mimeTypes = Arrays.asList(MIME_TYPES);

   protected void setMimeTypes(List<MimeType> mimeTypes) {
      Assert.notEmpty(mimeTypes, "MimeType List must not be empty");
      this.mimeTypes = List.copyOf(mimeTypes);
   }

   protected List<MimeType> getMimeTypes() {
      return this.mimeTypes;
   }

   protected boolean supportsMimeType(@Nullable MimeType mimeType) {
      if (mimeType == null) {
         return true;
      }

      for (MimeType supportedMimeType : MIME_TYPES) {
         if (supportedMimeType.isCompatibleWith(mimeType)) {
            return true;
         }
      }

      return false;
   }
}
