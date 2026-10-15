package org.springframework.http.codec.xml;

import com.fasterxml.aalto.AsyncByteBufferFeeder;
import com.fasterxml.aalto.AsyncXMLInputFactory;
import com.fasterxml.aalto.AsyncXMLStreamReader;
import com.fasterxml.aalto.evt.EventAllocatorImpl;
import com.fasterxml.aalto.stax.InputFactoryImpl;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;
import javax.xml.stream.util.XMLEventAllocator;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.core.ResolvableType;
import org.springframework.core.codec.AbstractDecoder;
import org.springframework.core.codec.DecodingException;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DataBuffer.ByteBufferIterator;
import org.springframework.http.MediaType;
import org.springframework.util.ClassUtils;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.xml.StaxUtils;
import reactor.core.publisher.Flux;

public class XmlEventDecoder extends AbstractDecoder<XMLEvent> {
   public static final String BYTE_TRACKER_HINT = XmlEventDecoder.class.getName() + ".byteTracker";
   private static final XMLInputFactory inputFactory = StaxUtils.createDefensiveInputFactory();
   private static final boolean AALTO_PRESENT = ClassUtils.isPresent("com.fasterxml.aalto.AsyncXMLStreamReader", XmlEventDecoder.class.getClassLoader());
   boolean useAalto = AALTO_PRESENT;
   private int maxInMemorySize = 262144;

   public XmlEventDecoder() {
      super(new MimeType[]{MimeTypeUtils.APPLICATION_XML, MimeTypeUtils.TEXT_XML, new MediaType("application", "*+xml")});
   }

   public void setMaxInMemorySize(int byteCount) {
      this.maxInMemorySize = byteCount;
   }

   public int getMaxInMemorySize() {
      return this.maxInMemorySize;
   }

   public Flux<XMLEvent> decode(Publisher<DataBuffer> input, ResolvableType elementType, @Nullable MimeType mimeType, @Nullable Map<String, Object> hints) {
      if (this.useAalto) {
         XmlEventDecoder.AaltoDataBufferToXmlEvent mapper = new XmlEventDecoder.AaltoDataBufferToXmlEvent(hints);
         return Flux.from(input).flatMapIterable(mapper).doFinally(signalType -> mapper.endOfInput());
      } else {
         return DataBufferUtils.join(input, this.maxInMemorySize).flatMapIterable(buffer -> {
            try {
               InputStream is = buffer.asInputStream();
               Iterator<Object> eventReader = inputFactory.createXMLEventReader(is);
               List<XMLEvent> result = new ArrayList<>();
               eventReader.forEachRemaining(event -> result.add(event));
               return result;
            } catch (XMLStreamException ex) {
               throw new DecodingException(ex.getMessage(), ex);
            } finally {
               DataBufferUtils.release(buffer);
            }
         });
      }
   }

   private static final class AaltoDataBufferToXmlEvent implements Function<DataBuffer, List<? extends XMLEvent>> {
      private static final AsyncXMLInputFactory inputFactory = (AsyncXMLInputFactory)StaxUtils.createDefensiveInputFactory(InputFactoryImpl::new);
      private final AsyncXMLStreamReader<AsyncByteBufferFeeder> streamReader = inputFactory.createAsyncForByteBuffer();
      private final XMLEventAllocator eventAllocator = EventAllocatorImpl.getDefaultInstance();
      private final XmlEventDecoder.@Nullable ReceivedByteTracker byteTracker;

      private AaltoDataBufferToXmlEvent(@Nullable Map<String, Object> hints) {
         this.byteTracker = hints != null ? (XmlEventDecoder.ReceivedByteTracker)hints.get(XmlEventDecoder.BYTE_TRACKER_HINT) : null;
      }

      public List<? extends XMLEvent> apply(DataBuffer dataBuffer) {
         try {
            if (this.byteTracker != null) {
               this.byteTracker.incrementByteCount(dataBuffer);
            }

            AsyncByteBufferFeeder inputFeeder = (AsyncByteBufferFeeder)this.streamReader.getInputFeeder();
            ByteBufferIterator iterator = dataBuffer.readableByteBuffers();

            try {
               while (iterator.hasNext()) {
                  inputFeeder.feedInput((ByteBuffer)iterator.next());
               }
            } catch (Throwable var12) {
               if (iterator != null) {
                  try {
                     iterator.close();
                  } catch (Throwable var11) {
                     var12.addSuppressed(var11);
                  }
               }

               throw var12;
            }

            if (iterator != null) {
               iterator.close();
            }

            List<XMLEvent> events = new ArrayList<>();

            while (this.streamReader.next() != 257) {
               XMLEvent event = this.eventAllocator.allocate(this.streamReader);
               events.add(event);
               if (event.isEndDocument()) {
                  break;
               }
            }

            return events;
         } catch (XMLStreamException ex) {
            throw new DecodingException(ex.getMessage(), ex);
         } finally {
            DataBufferUtils.release(dataBuffer);
         }
      }

      public void endOfInput() {
         ((AsyncByteBufferFeeder)this.streamReader.getInputFeeder()).endOfInput();
      }
   }

   public static class ReceivedByteTracker {
      public static final XmlEventDecoder.ReceivedByteTracker NO_OP = new XmlEventDecoder.ReceivedByteTracker(-1);
      private final int maxInMemorySize;
      private int byteCount;

      public ReceivedByteTracker(int maxInMemorySize) {
         this.maxInMemorySize = maxInMemorySize;
      }

      public int getMaxInMemorySize() {
         return this.maxInMemorySize;
      }

      public boolean isMaxInMemorySizeExceeded() {
         return this.maxInMemorySize != -1 && this.byteCount > this.maxInMemorySize;
      }

      public void reset() {
         this.byteCount = 0;
      }

      private void incrementByteCount(DataBuffer buffer) {
         if (this.maxInMemorySize != -1) {
            this.byteCount = this.byteCount + buffer.readableByteCount();
         }
      }

      @Override
      public String toString() {
         return this.byteCount + " bytes";
      }
   }
}
