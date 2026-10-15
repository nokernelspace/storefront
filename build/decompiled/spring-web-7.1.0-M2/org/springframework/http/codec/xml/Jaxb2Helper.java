package org.springframework.http.codec.xml;

import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlSchema;
import jakarta.xml.bind.annotation.XmlSeeAlso;
import jakarta.xml.bind.annotation.XmlType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.xml.namespace.QName;
import javax.xml.stream.events.XMLEvent;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SynchronousSink;

abstract class Jaxb2Helper {
   private static final String JAXB_DEFAULT_ANNOTATION_VALUE = "##default";

   public static Set<QName> toQNames(Class<?> clazz) {
      Set<QName> result = new HashSet<>(1);
      findQNames(clazz, result, new HashSet<>());
      return result;
   }

   private static void findQNames(Class<?> clazz, Set<QName> qNames, Set<Class<?>> completedClasses) {
      if (!completedClasses.contains(clazz)) {
         if (clazz.isAnnotationPresent(XmlRootElement.class)) {
            XmlRootElement annotation = clazz.getAnnotation(XmlRootElement.class);
            qNames.add(new QName(namespace(annotation.namespace(), clazz), localPart(annotation.name(), clazz)));
         } else {
            if (!clazz.isAnnotationPresent(XmlType.class)) {
               throw new IllegalArgumentException("Output class [" + clazz.getName() + "] is neither annotated with @XmlRootElement nor @XmlType");
            }

            XmlType annotation = clazz.getAnnotation(XmlType.class);
            qNames.add(new QName(namespace(annotation.namespace(), clazz), localPart(annotation.name(), clazz)));
         }

         completedClasses.add(clazz);
         if (clazz.isAnnotationPresent(XmlSeeAlso.class)) {
            XmlSeeAlso annotation = clazz.getAnnotation(XmlSeeAlso.class);

            for (Class<?> seeAlso : annotation.value()) {
               findQNames(seeAlso, qNames, completedClasses);
            }
         }
      }
   }

   private static String localPart(String value, Class<?> outputClass) {
      return "##default".equals(value) ? ClassUtils.getShortNameAsProperty(outputClass) : value;
   }

   private static String namespace(String value, Class<?> outputClass) {
      if ("##default".equals(value)) {
         Package outputClassPackage = outputClass.getPackage();
         if (outputClassPackage != null && outputClassPackage.isAnnotationPresent(XmlSchema.class)) {
            XmlSchema annotation = outputClassPackage.getAnnotation(XmlSchema.class);
            return annotation.namespace();
         } else {
            return "";
         }
      } else {
         return value;
      }
   }

   public static Flux<List<XMLEvent>> split(Flux<XMLEvent> xmlEventFlux, Set<QName> names, XmlEventDecoder.@Nullable ReceivedByteTracker byteTracker) {
      return xmlEventFlux.handle(new Jaxb2Helper.SplitHandler(names, byteTracker));
   }

   private static class SplitHandler implements BiConsumer<XMLEvent, SynchronousSink<List<XMLEvent>>> {
      private final Set<QName> names;
      private final XmlEventDecoder.ReceivedByteTracker byteTracker;
      private @Nullable List<XMLEvent> events;
      private int elementDepth = 0;
      private int barrier = Integer.MAX_VALUE;

      public SplitHandler(Set<QName> names, XmlEventDecoder.@Nullable ReceivedByteTracker byteTracker) {
         this.names = names;
         this.byteTracker = byteTracker != null ? byteTracker : XmlEventDecoder.ReceivedByteTracker.NO_OP;
      }

      public void accept(XMLEvent event, SynchronousSink<List<XMLEvent>> sink) {
         if (event.isStartElement()) {
            if (this.barrier == Integer.MAX_VALUE) {
               QName startElementName = event.asStartElement().getName();
               if (this.names.contains(startElementName)) {
                  this.events = new ArrayList<>();
                  this.barrier = this.elementDepth;
               }
            }

            this.elementDepth++;
         }

         if (this.elementDepth > this.barrier) {
            Assert.state(this.events != null, "No XMLEvent List");
            this.events.add(event);
         }

         if (event.isEndElement()) {
            this.elementDepth--;
            if (this.elementDepth == this.barrier) {
               Assert.state(this.events != null, "No XMLEvent List");
               sink.next(this.events);
               this.barrier = Integer.MAX_VALUE;
               this.events = null;
            }
         }

         if (this.events == null) {
            this.byteTracker.reset();
         } else if (this.byteTracker.isMaxInMemorySizeExceeded()) {
            throw new DataBufferLimitException("Exceeded limit on max bytes per XML node: " + this.byteTracker.getMaxInMemorySize());
         }
      }
   }
}
