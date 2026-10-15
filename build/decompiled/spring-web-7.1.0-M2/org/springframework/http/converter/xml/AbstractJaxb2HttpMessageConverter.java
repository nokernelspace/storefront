package org.springframework.http.converter.xml;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.Unmarshaller;
import java.nio.charset.Charset;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConversionException;

public abstract class AbstractJaxb2HttpMessageConverter<T> extends AbstractXmlHttpMessageConverter<T> {
   private final ConcurrentMap<Class<?>, JAXBContext> jaxbContexts = new ConcurrentHashMap<>(64);

   protected final Marshaller createMarshaller(Class<?> clazz) {
      try {
         JAXBContext jaxbContext = this.getJaxbContext(clazz);
         Marshaller marshaller = jaxbContext.createMarshaller();
         this.customizeMarshaller(marshaller);
         return marshaller;
      } catch (JAXBException ex) {
         throw new HttpMessageConversionException("Could not create Marshaller for class [" + clazz + "]: " + ex.getMessage(), ex);
      }
   }

   protected void customizeMarshaller(Marshaller marshaller) {
   }

   protected final Unmarshaller createUnmarshaller(Class<?> clazz) {
      try {
         JAXBContext jaxbContext = this.getJaxbContext(clazz);
         Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
         this.customizeUnmarshaller(unmarshaller);
         return unmarshaller;
      } catch (JAXBException ex) {
         throw new HttpMessageConversionException("Could not create Unmarshaller for class [" + clazz + "]: " + ex.getMessage(), ex);
      }
   }

   protected void customizeUnmarshaller(Unmarshaller unmarshaller) {
   }

   protected final JAXBContext getJaxbContext(Class<?> clazz) {
      return this.jaxbContexts.computeIfAbsent(clazz, key -> {
         try {
            return JAXBContext.newInstance(new Class[]{clazz});
         } catch (JAXBException ex) {
            throw new HttpMessageConversionException("Could not create JAXBContext for class [" + clazz + "]: " + ex.getMessage(), ex);
         }
      });
   }

   protected @Nullable Charset detectCharset(HttpHeaders httpHeaders) {
      MediaType contentType = httpHeaders.getContentType();
      return contentType != null && contentType.getCharset() != null ? contentType.getCharset() : null;
   }
}
