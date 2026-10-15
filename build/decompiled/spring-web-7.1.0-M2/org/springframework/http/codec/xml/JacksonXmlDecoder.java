package org.springframework.http.codec.xml;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.http.codec.AbstractJacksonDecoder;
import org.springframework.util.MimeType;
import org.springframework.util.xml.StaxUtils;
import reactor.core.publisher.Flux;
import tools.jackson.dataformat.xml.XmlFactory;
import tools.jackson.dataformat.xml.XmlMapper;
import tools.jackson.dataformat.xml.XmlMapper.Builder;

public class JacksonXmlDecoder extends AbstractJacksonDecoder<XmlMapper> {
   private static final MediaType[] DEFAULT_XML_MIME_TYPES = new MediaType[]{
      new MediaType("application", "xml", StandardCharsets.UTF_8),
      new MediaType("text", "xml", StandardCharsets.UTF_8),
      new MediaType("application", "*+xml", StandardCharsets.UTF_8)
   };

   public JacksonXmlDecoder() {
      super(XmlMapper.builder(defensiveXmlFactory()), DEFAULT_XML_MIME_TYPES);
   }

   public JacksonXmlDecoder(Builder builder) {
      super(builder, DEFAULT_XML_MIME_TYPES);
   }

   public JacksonXmlDecoder(XmlMapper mapper) {
      super(mapper, DEFAULT_XML_MIME_TYPES);
   }

   public JacksonXmlDecoder(Builder builder, MimeType... mimeTypes) {
      super(builder, mimeTypes);
   }

   public JacksonXmlDecoder(XmlMapper mapper, MimeType... mimeTypes) {
      super(mapper, mimeTypes);
   }

   @Override
   public boolean canDecode(ResolvableType elementType, @Nullable MimeType mimeType) {
      return super.canDecode(elementType, mimeType) && String.class != elementType.toClass();
   }

   @Override
   public Flux<Object> decode(Publisher<DataBuffer> input, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {
      throw new UnsupportedOperationException("Stream decoding is currently not supported");
   }

   public static XmlFactory defensiveXmlFactory() {
      return new XmlFactory(StaxUtils.createDefensiveInputFactory());
   }
}
