package org.springframework.http.codec.xml;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.AbstractJacksonEncoder;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.dataformat.xml.XmlMapper.Builder;

public class JacksonXmlEncoder extends AbstractJacksonEncoder<XmlMapper> {
   private static final MediaType[] DEFAULT_XML_MIME_TYPES = new MediaType[]{
      new MediaType("application", "xml", StandardCharsets.UTF_8),
      new MediaType("text", "xml", StandardCharsets.UTF_8),
      new MediaType("application", "*+xml", StandardCharsets.UTF_8)
   };

   public JacksonXmlEncoder() {
      super(XmlMapper.builder(), DEFAULT_XML_MIME_TYPES);
   }

   public JacksonXmlEncoder(Builder builder) {
      super(builder, DEFAULT_XML_MIME_TYPES);
   }

   public JacksonXmlEncoder(XmlMapper mapper) {
      super(mapper, DEFAULT_XML_MIME_TYPES);
   }

   public JacksonXmlEncoder(Builder builder, MimeType... mimeTypes) {
      super(builder, mimeTypes);
   }

   public JacksonXmlEncoder(XmlMapper mapper, MimeType... mimeTypes) {
      super(mapper, mimeTypes);
   }

   @Override
   public boolean canEncode(ResolvableType elementType, @Nullable MimeType mimeType) {
      return super.canEncode(elementType, mimeType) && String.class != elementType.toClass();
   }

   @Override
   public Flux<DataBuffer> encode(
      Publisher<?> inputStream, DataBufferFactory bufferFactory, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints
   ) {
      throw new UnsupportedOperationException("Stream encoding is currently not supported");
   }
}
