package org.springframework.core.codec;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.util.MimeType;
import reactor.core.publisher.Mono;

public abstract class AbstractDecoder<T> implements Decoder<T> {
   private final List<MimeType> decodableMimeTypes;
   protected Log logger = LogFactory.getLog(this.getClass());

   protected AbstractDecoder(MimeType... supportedMimeTypes) {
      this.decodableMimeTypes = Arrays.asList(supportedMimeTypes);
   }

   public void setLogger(Log logger) {
      this.logger = logger;
   }

   public Log getLogger() {
      return this.logger;
   }

   @Override
   public List<MimeType> getDecodableMimeTypes() {
      return this.decodableMimeTypes;
   }

   @Override
   public boolean canDecode(ResolvableType elementType, @Nullable MimeType mimeType) {
      if (mimeType == null) {
         return true;
      }

      for (MimeType candidate : this.decodableMimeTypes) {
         if (candidate.isCompatibleWith(mimeType)) {
            return true;
         }
      }

      return false;
   }

   @Override
   public Mono<T> decodeToMono(Publisher<DataBuffer> inputStream, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {
      throw new UnsupportedOperationException();
   }
}
