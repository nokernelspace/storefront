package org.springframework.core.codec;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

public final class CharBufferDecoder extends AbstractCharSequenceDecoder<CharBuffer> {
   public CharBufferDecoder(List<String> delimiters, boolean stripDelimiter, MimeType... mimeTypes) {
      super(delimiters, stripDelimiter, mimeTypes);
   }

   @Override
   public boolean canDecode(ResolvableType elementType, @Nullable MimeType mimeType) {
      return elementType.resolve() == CharBuffer.class && super.canDecode(elementType, mimeType);
   }

   protected CharBuffer decodeInternal(DataBuffer dataBuffer, Charset charset) {
      ByteBuffer byteBuffer = ByteBuffer.allocate(dataBuffer.readableByteCount());
      dataBuffer.toByteBuffer(byteBuffer);
      return charset.decode(byteBuffer);
   }

   public static CharBufferDecoder textPlainOnly() {
      return textPlainOnly(DEFAULT_DELIMITERS, true);
   }

   public static CharBufferDecoder textPlainOnly(List<String> delimiters, boolean stripDelimiter) {
      MimeType mimeType = new MimeType("text", "plain", DEFAULT_CHARSET);
      return new CharBufferDecoder(delimiters, stripDelimiter, mimeType);
   }

   public static CharBufferDecoder allMimeTypes() {
      return allMimeTypes(DEFAULT_DELIMITERS, true);
   }

   public static CharBufferDecoder allMimeTypes(List<String> delimiters, boolean stripDelimiter) {
      MimeType mimeType = new MimeType("text", "plain", DEFAULT_CHARSET);
      return new CharBufferDecoder(delimiters, stripDelimiter, mimeType, MimeTypeUtils.ALL);
   }
}
