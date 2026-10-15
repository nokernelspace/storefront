package org.springframework.http.converter.xml;

import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.MarshalException;
import jakarta.xml.bind.Marshaller;
import jakarta.xml.bind.PropertyException;
import jakarta.xml.bind.UnmarshalException;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.XmlRootElement;
import jakarta.xml.bind.annotation.XmlType;
import java.io.StringReader;
import java.nio.charset.Charset;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;
import org.jspecify.annotations.Nullable;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.util.ClassUtils;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

public class Jaxb2RootElementHttpMessageConverter extends AbstractJaxb2HttpMessageConverter<Object> {
   private static final EntityResolver NO_OP_ENTITY_RESOLVER = (publicId, systemId) -> new InputSource(new StringReader(""));
   private boolean supportDtd = false;
   private boolean processExternalEntities = false;
   private volatile @Nullable SAXParserFactory sourceParserFactory;

   public void setSupportDtd(boolean supportDtd) {
      this.supportDtd = supportDtd;
      this.sourceParserFactory = null;
   }

   public boolean isSupportDtd() {
      return this.supportDtd;
   }

   public void setProcessExternalEntities(boolean processExternalEntities) {
      this.processExternalEntities = processExternalEntities;
      if (processExternalEntities) {
         this.supportDtd = true;
      }

      this.sourceParserFactory = null;
   }

   public boolean isProcessExternalEntities() {
      return this.processExternalEntities;
   }

   @Override
   public boolean canRead(Class<?> clazz, @Nullable MediaType mediaType) {
      return (clazz.isAnnotationPresent(XmlRootElement.class) || clazz.isAnnotationPresent(XmlType.class)) && this.canRead(mediaType);
   }

   @Override
   public boolean canWrite(Class<?> clazz, @Nullable MediaType mediaType) {
      boolean supportedType = JAXBElement.class.isAssignableFrom(clazz) || AnnotationUtils.findAnnotation(clazz, XmlRootElement.class) != null;
      return supportedType && this.canWrite(mediaType);
   }

   @Override
   public boolean canWriteRepeatedly(Object o, @Nullable MediaType contentType) {
      return true;
   }

   @Override
   protected boolean supports(Class<?> clazz) {
      throw new UnsupportedOperationException();
   }

   @Override
   protected Object readFromSource(Class<?> clazz, HttpHeaders headers, Source source) throws Exception {
      try {
         source = this.processSource(source, this.detectCharset(headers));
         Unmarshaller unmarshaller = this.createUnmarshaller(clazz);
         if (clazz.isAnnotationPresent(XmlRootElement.class)) {
            return unmarshaller.unmarshal(source);
         }

         JAXBElement<?> jaxbElement = unmarshaller.unmarshal(source, clazz);
         return jaxbElement.getValue();
      } catch (NullPointerException ex) {
         if (!this.isSupportDtd()) {
            throw new IllegalStateException("NPE while unmarshalling. This can happen due to the presence of DTD declarations which are disabled.", ex);
         } else {
            throw ex;
         }
      } catch (UnmarshalException ex) {
         throw ex;
      } catch (JAXBException ex) {
         throw new HttpMessageConversionException("Invalid JAXB setup: " + ex.getMessage(), ex);
      }
   }

   protected Source processSource(Source source, @Nullable Charset charset) {
      if (source instanceof StreamSource streamSource) {
         InputSource inputSource = new InputSource(streamSource.getInputStream());
         if (charset != null) {
            inputSource.setEncoding(charset.name());
         }

         try {
            SAXParserFactory factory = this.sourceParserFactory;
            if (factory == null) {
               factory = SAXParserFactory.newInstance();
               factory.setNamespaceAware(true);

               try {
                  factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", !this.isSupportDtd());
               } catch (Exception var9) {
               }

               try {
                  factory.setFeature("http://xml.org/sax/features/external-general-entities", this.isProcessExternalEntities());
                  factory.setFeature("http://xml.org/sax/features/external-parameter-entities", this.isProcessExternalEntities());
               } catch (Exception var8) {
               }

               this.sourceParserFactory = factory;
            }

            SAXParser saxParser = factory.newSAXParser();
            XMLReader xmlReader = saxParser.getXMLReader();
            if (!this.isProcessExternalEntities()) {
               xmlReader.setEntityResolver(NO_OP_ENTITY_RESOLVER);
            }

            return new SAXSource(xmlReader, inputSource);
         } catch (Exception ex) {
            this.logger.warn("Processing of external entities could not be disabled", ex);
            return source;
         }
      } else {
         return source;
      }
   }

   @Override
   protected void writeToResult(Object value, HttpHeaders headers, Result result) throws Exception {
      try {
         Class<?> clazz = getMarshallerType(value);
         Marshaller marshaller = this.createMarshaller(clazz);
         this.setCharset(headers.getContentType(), marshaller);
         marshaller.marshal(value, result);
      } catch (MarshalException ex) {
         throw ex;
      } catch (JAXBException ex) {
         throw new HttpMessageConversionException("Invalid JAXB setup: " + ex.getMessage(), ex);
      }
   }

   private static Class<?> getMarshallerType(Object value) {
      return value instanceof JAXBElement<?> jaxbElement ? jaxbElement.getDeclaredType() : ClassUtils.getUserClass(value);
   }

   private void setCharset(@Nullable MediaType contentType, Marshaller marshaller) throws PropertyException {
      if (contentType != null && contentType.getCharset() != null) {
         marshaller.setProperty("jaxb.encoding", contentType.getCharset().name());
      }
   }

   @Override
   protected boolean supportsRepeatableWrites(Object o) {
      return true;
   }
}
