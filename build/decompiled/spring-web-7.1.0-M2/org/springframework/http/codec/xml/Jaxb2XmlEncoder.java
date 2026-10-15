package org.springframework.http.codec.xml;

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.MarshalException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractSingleValueEncoder;
import org.springframework.core.codec.CodecException;
import org.springframework.core.codec.EncodingException;
import org.springframework.core.codec.Hints;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.log.LogFormatUtils;
import org.springframework.http.MediaType;
import org.springframework.util.ClassUtils;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class Jaxb2XmlEncoder extends AbstractSingleValueEncoder<Object> {
   private final JaxbContextContainer jaxbContexts = new JaxbContextContainer();
   private Function<Marshaller, Marshaller> marshallerProcessor = Function.identity();

   public Jaxb2XmlEncoder() {
      super(new MimeType[]{MimeTypeUtils.APPLICATION_XML, MimeTypeUtils.TEXT_XML, new MediaType("application", "*+xml")});
   }

   public void setMarshallerProcessor(Function<Marshaller, Marshaller> processor) {
      this.marshallerProcessor = this.marshallerProcessor.andThen(processor);
   }

   public Function<Marshaller, Marshaller> getMarshallerProcessor() {
      return this.marshallerProcessor;
   }

   public boolean canEncode(ResolvableType elementType, @Nullable MimeType mimeType) {
      if (!super.canEncode(elementType, mimeType)) {
         return false;
      }

      Class<?> outputClass = elementType.toClass();
      return outputClass.isAnnotationPresent(XmlRootElement.class)
         || outputClass.isAnnotationPresent(XmlType.class)
         || JAXBElement.class.isAssignableFrom(outputClass);
   }

   protected Flux<DataBuffer> encode(
      Object value, DataBufferFactory bufferFactory, ResolvableType valueType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints
   ) {
      return Mono.fromCallable(() -> this.encodeValue(value, bufferFactory, valueType, mimeType, hints)).flux();
   }

   public DataBuffer encodeValue(
      Object value, DataBufferFactory bufferFactory, ResolvableType valueType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints
   ) {
      if (!Hints.isLoggingSuppressed(hints)) {
         LogFormatUtils.traceDebug(this.logger, traceOn -> {
            String formatted = LogFormatUtils.formatValue(value, !traceOn);
            return Hints.getLogPrefix(hints) + "Encoding [" + formatted + "]";
         });
      }

      boolean release = true;
      DataBuffer buffer = bufferFactory.allocateBuffer(1024);

      try {
         OutputStream outputStream = buffer.asOutputStream();
         Class<?> clazz = getMarshallerType(value);
         Marshaller marshaller = this.initMarshaller(clazz);
         marshaller.marshal(value, outputStream);
         release = false;
         return buffer;
      } catch (MarshalException ex) {
         throw new EncodingException("Could not marshal " + value.getClass() + " to XML", ex);
      } catch (JAXBException ex) {
         throw new CodecException("Invalid JAXB configuration", ex);
      } finally {
         if (release) {
            DataBufferUtils.release(buffer);
         }
      }
   }

   private static Class<?> getMarshallerType(Object value) {
      return value instanceof JAXBElement<?> jaxbElement ? jaxbElement.getDeclaredType() : ClassUtils.getUserClass(value);
   }

   private Marshaller initMarshaller(Class<?> clazz) throws CodecException, JAXBException {
      Marshaller marshaller = this.jaxbContexts.createMarshaller(clazz);
      marshaller.setProperty("jaxb.encoding", StandardCharsets.UTF_8.name());
      return this.marshallerProcessor.apply(marshaller);
   }
}
