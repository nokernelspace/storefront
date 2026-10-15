package org.springframework.http.converter.xml;

import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.UnmarshalException;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.GenericHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.util.ReflectionUtils;
import org.springframework.util.xml.StaxUtils;

public class Jaxb2CollectionHttpMessageConverter<T extends Collection> extends AbstractJaxb2HttpMessageConverter<T> implements GenericHttpMessageConverter<T> {
   private final XMLInputFactory inputFactory = this.createXmlInputFactory();

   @Override
   public boolean canRead(Class<?> clazz, @Nullable MediaType mediaType) {
      return false;
   }

   @Override
   public boolean canRead(Type type, @Nullable Class<?> contextClass, @Nullable MediaType mediaType) {
      if (!(type instanceof ParameterizedType parameterizedType)) {
         return false;
      } else if (parameterizedType.getRawType() instanceof Class<?> rawType) {
         if (!Collection.class.isAssignableFrom(rawType)) {
            return false;
         } else if (parameterizedType.getActualTypeArguments().length != 1) {
            return false;
         } else {
            return !(parameterizedType.getActualTypeArguments()[0] instanceof Class<?> typeArgumentClass)
               ? false
               : (typeArgumentClass.isAnnotationPresent(XmlRootElement.class) || typeArgumentClass.isAnnotationPresent(XmlType.class))
                  && this.canRead(mediaType);
         }
      } else {
         return false;
      }
   }

   @Override
   public boolean canWrite(Class<?> clazz, @Nullable MediaType mediaType) {
      return false;
   }

   @Override
   public boolean canWrite(@Nullable Type type, @Nullable Class<?> clazz, @Nullable MediaType mediaType) {
      return false;
   }

   @Override
   protected boolean supports(Class<?> clazz) {
      throw new UnsupportedOperationException();
   }

   protected T readFromSource(Class<? extends T> clazz, HttpHeaders headers, Source source) throws Exception {
      throw new UnsupportedOperationException();
   }

   public T read(Type type, @Nullable Class<?> contextClass, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
      ParameterizedType parameterizedType = (ParameterizedType)type;
      T result = this.createCollection((Class<?>)parameterizedType.getRawType());
      Class<?> elementClass = (Class<?>)parameterizedType.getActualTypeArguments()[0];

      try {
         Unmarshaller unmarshaller = this.createUnmarshaller(elementClass);
         Charset detectedCharset = this.detectCharset(inputMessage.getHeaders());
         XMLStreamReader streamReader = detectedCharset != null
            ? this.inputFactory.createXMLStreamReader(inputMessage.getBody(), detectedCharset.name())
            : this.inputFactory.createXMLStreamReader(inputMessage.getBody());

         for (int event = this.moveToFirstChildOfRootElement(streamReader); event != 8; event = this.moveToNextElement(streamReader)) {
            if (elementClass.isAnnotationPresent(XmlRootElement.class)) {
               result.add(unmarshaller.unmarshal(streamReader));
            } else {
               if (!elementClass.isAnnotationPresent(XmlType.class)) {
                  throw new HttpMessageNotReadableException("Cannot unmarshal to [" + elementClass + "]", inputMessage);
               }

               result.add(unmarshaller.unmarshal(streamReader, elementClass).getValue());
            }
         }

         return result;
      } catch (XMLStreamException ex) {
         throw new HttpMessageNotReadableException("Failed to read XML stream: " + ex.getMessage(), ex, inputMessage);
      } catch (UnmarshalException ex) {
         throw new HttpMessageNotReadableException("Could not unmarshal to [" + elementClass + "]: " + ex, ex, inputMessage);
      } catch (JAXBException ex) {
         throw new HttpMessageConversionException("Invalid JAXB setup: " + ex.getMessage(), ex);
      }
   }

   protected T createCollection(Class<?> collectionClass) {
      if (!collectionClass.isInterface()) {
         try {
            return (T)ReflectionUtils.accessibleConstructor(collectionClass, new Class[0]).newInstance();
         } catch (Throwable ex) {
            throw new IllegalArgumentException("Could not instantiate collection class: " + collectionClass.getName(), ex);
         }
      } else if (List.class == collectionClass) {
         return (T)(new ArrayList());
      } else {
         return (T)(SortedSet.class == collectionClass ? new TreeSet() : new LinkedHashSet());
      }
   }

   private int moveToFirstChildOfRootElement(XMLStreamReader streamReader) throws XMLStreamException {
      int event = streamReader.next();

      while (event != 1) {
         event = streamReader.next();
      }

      event = streamReader.next();

      while (event != 1 && event != 8) {
         event = streamReader.next();
      }

      return event;
   }

   private int moveToNextElement(XMLStreamReader streamReader) throws XMLStreamException {
      int event = streamReader.getEventType();

      while (event != 1 && event != 8) {
         event = streamReader.next();
      }

      return event;
   }

   public void write(T t, @Nullable Type type, @Nullable MediaType contentType, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
      throw new UnsupportedOperationException();
   }

   protected void writeToResult(T t, HttpHeaders headers, Result result) throws Exception {
      throw new UnsupportedOperationException();
   }

   protected XMLInputFactory createXmlInputFactory() {
      return StaxUtils.createDefensiveInputFactory();
   }
}
