package org.springframework.core.codec;

import java.nio.ByteBuffer;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

public class ByteBufferDecoder extends AbstractDataBufferDecoder<ByteBuffer> {
   public ByteBufferDecoder() {
      super(MimeTypeUtils.ALL);
   }

   @Override
   public boolean canDecode(ResolvableType elementType, @Nullable MimeType mimeType) {
      return ByteBuffer.class.isAssignableFrom(elementType.toClass()) && super.canDecode(elementType, mimeType);
   }

   public ByteBuffer decode(DataBuffer dataBuffer, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {
      int len = dataBuffer.readableByteCount();
      ByteBuffer result = ByteBuffer.allocate(len);
      dataBuffer.toByteBuffer(result);
      if (this.logger.isDebugEnabled()) {
         this.logger.debug(Hints.getLogPrefix(hints) + "Read " + len + " bytes");
      }

      DataBufferUtils.release(dataBuffer);
      return result;
   }
}
