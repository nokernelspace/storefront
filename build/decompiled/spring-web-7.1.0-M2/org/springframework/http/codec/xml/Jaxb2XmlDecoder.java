package org.springframework.http.codec.xml;

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.UnmarshalException;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlSeeAlso;
import jakarta.xml.bind.annotation.XmlType;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractDecoder;
import org.springframework.core.codec.CodecException;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.codec.Hints;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.MediaType;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.xml.StaxUtils;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class Jaxb2XmlDecoder extends AbstractDecoder<Object> {
   private static final XMLInputFactory inputFactory = StaxUtils.createDefensiveInputFactory();
   private final XmlEventDecoder xmlEventDecoder = new XmlEventDecoder();
   private final JaxbContextContainer jaxbContexts = new JaxbContextContainer();
   private Function<Unmarshaller, Unmarshaller> unmarshallerProcessor = Function.identity();
   private int maxInMemorySize = 262144;

   public Jaxb2XmlDecoder() {
      super(new MimeType[]{MimeTypeUtils.APPLICATION_XML, MimeTypeUtils.TEXT_XML, new MediaType("application", "*+xml")});
   }

   public Jaxb2XmlDecoder(MimeType... supportedMimeTypes) {
      super(supportedMimeTypes);
   }

   public void setUnmarshallerProcessor(Function<Unmarshaller, Unmarshaller> processor) {
      this.unmarshallerProcessor = this.unmarshallerProcessor.andThen(processor);
   }

   public Function<Unmarshaller, Unmarshaller> getUnmarshallerProcessor() {
      return this.unmarshallerProcessor;
   }

   public void setMaxInMemorySize(int byteCount) {
      this.maxInMemorySize = byteCount;
      this.xmlEventDecoder.setMaxInMemorySize(byteCount);
   }

   public int getMaxInMemorySize() {
      return this.maxInMemorySize;
   }

   public boolean canDecode(ResolvableType elementType, @Nullable MimeType mimeType) {
      Class<?> outputClass = elementType.toClass();
      return (outputClass.isAnnotationPresent(XmlRootElement.class) || outputClass.isAnnotationPresent(XmlType.class))
         && super.canDecode(elementType, mimeType);
   }

   public Flux<Object> decode(Publisher<DataBuffer> inputStream, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {
      XmlEventDecoder.ReceivedByteTracker byteTracker = new XmlEventDecoder.ReceivedByteTracker(this.maxInMemorySize);
      Flux<XMLEvent> xmlEventFlux = this.xmlEventDecoder
         .decode(inputStream, ResolvableType.forClass(XMLEvent.class), mimeType, Hints.merge(hints, XmlEventDecoder.BYTE_TRACKER_HINT, byteTracker));
      Class<?> outputClass = elementType.toClass();
      Set<QName> typeNames = Jaxb2Helper.toQNames(outputClass);
      Flux<List<XMLEvent>> splitEvents = Jaxb2Helper.split(xmlEventFlux, typeNames, byteTracker);
      return splitEvents.map(events -> {
         Object value = this.unmarshal(events, outputClass);
         LogFormatUtils.traceDebug(this.logger, traceOn -> {
            String formatted = LogFormatUtils.formatValue(value, !traceOn);
            return Hints.getLogPrefix(hints) + "Decoded [" + formatted + "]";
         });
         return value;
      });
   }

   public Mono<Object> decodeToMono(Publisher<DataBuffer> input, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {
      return DataBufferUtils.join(input, this.maxInMemorySize).map(dataBuffer -> this.decode(dataBuffer, elementType, mimeType, hints));
   }

   public Object decode(DataBuffer dataBuffer, ResolvableType targetType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints) throws DecodingException {
      try {
         Iterator<Object> eventReader = inputFactory.createXMLEventReader(dataBuffer.asInputStream(), encoding(mimeType));
         List<XMLEvent> events = new ArrayList<>();
         eventReader.forEachRemaining(event -> events.add(event));
         return this.unmarshal(events, targetType.toClass());
      } catch (XMLStreamException ex) {
         throw new DecodingException(ex.getMessage(), ex);
      } catch (Throwable ex) {
         Throwable cause = ex.getCause();
         if (cause instanceof XMLStreamException) {
            throw new DecodingException(cause.getMessage(), cause);
         } else {
            throw Exceptions.propagate(ex);
         }
      } finally {
         DataBufferUtils.release(dataBuffer);
      }
   }

   private static @Nullable String encoding(@Nullable MimeType mimeType) {
      if (mimeType == null) {
         return null;
      }

      Charset charset = mimeType.getCharset();
      return charset == null ? null : charset.name();
   }

   private Object unmarshal(List<XMLEvent> events, Class<?> outputClass) {
      try {
         Unmarshaller unmarshaller = this.initUnmarshaller(outputClass);
         XMLEventReader eventReader = StaxUtils.createXMLEventReader(events);
         if (!outputClass.isAnnotationPresent(XmlRootElement.class) && !outputClass.isAnnotationPresent(XmlSeeAlso.class)) {
            JAXBElement<?> jaxbElement = unmarshaller.unmarshal(eventReader, outputClass);
            return jaxbElement.getValue();
         } else {
            return unmarshaller.unmarshal(eventReader);
         }
      } catch (UnmarshalException ex) {
         throw new DecodingException("Could not unmarshal XML to " + outputClass, ex);
      } catch (JAXBException ex) {
         throw new CodecException("Invalid JAXB configuration", ex);
      }
   }

   private Unmarshaller initUnmarshaller(Class<?> outputClass) throws CodecException, JAXBException {
      Unmarshaller unmarshaller = this.jaxbContexts.createUnmarshaller(outputClass);
      return this.unmarshallerProcessor.apply(unmarshaller);
   }
}
