package org.springframework.core.codec;

import java.nio.charset.Charset;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

public final class StringDecoder extends AbstractCharSequenceDecoder<String> {
   private StringDecoder(List<String> delimiters, boolean stripDelimiter, MimeType... mimeTypes) {
      super(delimiters, stripDelimiter, mimeTypes);
   }

   @Override
   public boolean canDecode(ResolvableType elementType, @Nullable MimeType mimeType) {
      return elementType.resolve() == String.class && super.canDecode(elementType, mimeType);
   }

   protected String decodeInternal(DataBuffer dataBuffer, Charset charset) {
      return dataBuffer.toString(charset);
   }

   public static StringDecoder textPlainOnly() {
      return textPlainOnly(DEFAULT_DELIMITERS, true);
   }

   public static StringDecoder textPlainOnly(List<String> delimiters, boolean stripDelimiter) {
      return new StringDecoder(delimiters, stripDelimiter, new MimeType("text", "plain", DEFAULT_CHARSET));
   }

   public static StringDecoder allMimeTypes() {
      return allMimeTypes(DEFAULT_DELIMITERS, true);
   }

   public static StringDecoder allMimeTypes(List<String> delimiters, boolean stripDelimiter) {
      return new StringDecoder(delimiters, stripDelimiter, new MimeType("text", "plain", DEFAULT_CHARSET), MimeTypeUtils.ALL);
   }
}
