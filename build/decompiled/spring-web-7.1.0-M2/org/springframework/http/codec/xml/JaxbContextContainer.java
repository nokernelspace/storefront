package org.springframework.http.codec.xml;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.core.codec.CodecException;

final class JaxbContextContainer {
   private final ConcurrentMap<Class<?>, JAXBContext> jaxbContexts = new ConcurrentHashMap<>(64);

   public Marshaller createMarshaller(Class<?> clazz) throws CodecException, JAXBException {
      JAXBContext jaxbContext = this.getJaxbContext(clazz);
      return jaxbContext.createMarshaller();
   }

   public Unmarshaller createUnmarshaller(Class<?> clazz) throws CodecException, JAXBException {
      JAXBContext jaxbContext = this.getJaxbContext(clazz);
      return jaxbContext.createUnmarshaller();
   }

   private JAXBContext getJaxbContext(Class<?> clazz) throws CodecException {
      return this.jaxbContexts.computeIfAbsent(clazz, key -> {
         try {
            return this.createJaxbContext(clazz);
         } catch (JAXBException ex) {
            throw new CodecException("Could not create JAXBContext for class [" + clazz + "]: " + ex.getMessage(), ex);
         }
      });
   }

   private JAXBContext createJaxbContext(Class<?> clazz) throws JAXBException {
      ClassLoader currentClassLoader = Thread.currentThread().getContextClassLoader();

      try {
         Thread.currentThread().setContextClassLoader(clazz.getClassLoader());
         return JAXBContext.newInstance(new Class[]{clazz});
      } finally {
         Thread.currentThread().setContextClassLoader(currentClassLoader);
      }
   }
}
