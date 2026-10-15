package org.springframework.http.converter.xml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.util.StreamUtils;
import org.w3c.dom.Document;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

public class SourceHttpMessageConverter<T extends Source> extends AbstractHttpMessageConverter<T> {
   private static final EntityResolver NO_OP_ENTITY_RESOLVER = (publicId, systemId) -> new InputSource(new StringReader(""));
   private static final XMLResolver NO_OP_XML_RESOLVER = (publicID, systemID, base, ns) -> InputStream.nullInputStream();
   private static final Set<Class<?>> SUPPORTED_CLASSES = Set.of(DOMSource.class, SAXSource.class, StAXSource.class, StreamSource.class, Source.class);
   private final TransformerFactory transformerFactory = TransformerFactory.newInstance();
   private boolean supportDtd = false;
   private boolean processExternalEntities = false;
   private volatile @Nullable DocumentBuilderFactory documentBuilderFactory;
   private volatile @Nullable SAXParserFactory saxParserFactory;
   private volatile @Nullable XMLInputFactory xmlInputFactory;

   public SourceHttpMessageConverter() {
      super(MediaType.APPLICATION_XML, MediaType.TEXT_XML, new MediaType("application", "*+xml"));
   }

   public void setSupportDtd(boolean supportDtd) {
      this.supportDtd = supportDtd;
      this.documentBuilderFactory = null;
      this.saxParserFactory = null;
      this.xmlInputFactory = null;
   }

   public boolean isSupportDtd() {
      return this.supportDtd;
   }

   public void setProcessExternalEntities(boolean processExternalEntities) {
      this.processExternalEntities = processExternalEntities;
      if (processExternalEntities) {
         this.supportDtd = true;
      }

      this.documentBuilderFactory = null;
      this.saxParserFactory = null;
      this.xmlInputFactory = null;
   }

   public boolean isProcessExternalEntities() {
      return this.processExternalEntities;
   }

   @Override
   public boolean supports(Class<?> clazz) {
      return SUPPORTED_CLASSES.contains(clazz);
   }

   protected T readInternal(Class<? extends T> clazz, HttpInputMessage inputMessage) throws IOException, HttpMessageNotReadableException {
      InputStream body = StreamUtils.nonClosing(inputMessage.getBody());
      if (DOMSource.class == clazz) {
         return (T)this.readDOMSource(body, inputMessage);
      } else if (SAXSource.class == clazz) {
         return (T)this.readSAXSource(body, inputMessage);
      } else if (StAXSource.class == clazz) {
         return (T)this.readStAXSource(body, inputMessage);
      } else if (StreamSource.class != clazz && Source.class != clazz) {
         throw new HttpMessageNotReadableException(
            "Could not read class [" + clazz + "]. Only DOMSource, SAXSource, StAXSource, and StreamSource are supported.", inputMessage
         );
      } else {
         return (T)this.readStreamSource(body);
      }
   }

   private DOMSource readDOMSource(InputStream body, HttpInputMessage inputMessage) throws IOException {
      try {
         DocumentBuilderFactory factory = this.documentBuilderFactory;
         if (factory == null) {
            factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);

            try {
               factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", !this.isSupportDtd());
            } catch (Exception var7) {
            }

            try {
               factory.setFeature("http://xml.org/sax/features/external-general-entities", this.isProcessExternalEntities());
               factory.setFeature("http://xml.org/sax/features/external-parameter-entities", this.isProcessExternalEntities());
            } catch (Exception var6) {
            }

            this.documentBuilderFactory = factory;
         }

         DocumentBuilder builder = factory.newDocumentBuilder();
         if (!this.isProcessExternalEntities()) {
            builder.setEntityResolver(NO_OP_ENTITY_RESOLVER);
         }

         Document document = builder.parse(body);
         return new DOMSource(document);
      } catch (NullPointerException ex) {
         if (!this.isSupportDtd()) {
            throw new HttpMessageNotReadableException(
               "NPE while unmarshalling: This can happen due to the presence of DTD declarations which are disabled.", ex, inputMessage
            );
         } else {
            throw ex;
         }
      } catch (ParserConfigurationException ex) {
         throw new HttpMessageNotReadableException("Could not set feature: " + ex.getMessage(), ex, inputMessage);
      } catch (SAXException ex) {
         throw new HttpMessageNotReadableException("Could not parse document: " + ex.getMessage(), ex, inputMessage);
      }
   }

   private SAXSource readSAXSource(InputStream body, HttpInputMessage inputMessage) throws IOException {
      try {
         SAXParserFactory factory = this.saxParserFactory;
         if (factory == null) {
            factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);

            try {
               factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", !this.isSupportDtd());
            } catch (Exception var8) {
            }

            try {
               factory.setFeature("http://xml.org/sax/features/external-general-entities", this.isProcessExternalEntities());
               factory.setFeature("http://xml.org/sax/features/external-parameter-entities", this.isProcessExternalEntities());
            } catch (Exception var7) {
            }

            this.saxParserFactory = factory;
         }

         SAXParser saxParser = factory.newSAXParser();
         XMLReader xmlReader = saxParser.getXMLReader();
         if (!this.isProcessExternalEntities()) {
            xmlReader.setEntityResolver(NO_OP_ENTITY_RESOLVER);
         }

         byte[] bytes = StreamUtils.copyToByteArray(body);
         return new SAXSource(xmlReader, new InputSource(new ByteArrayInputStream(bytes)));
      } catch (ParserConfigurationException ex) {
         throw new HttpMessageNotReadableException("Could not set feature: " + ex.getMessage(), ex, inputMessage);
      } catch (SAXException ex) {
         throw new HttpMessageNotReadableException("Could not parse document: " + ex.getMessage(), ex, inputMessage);
      }
   }

   private Source readStAXSource(InputStream body, HttpInputMessage inputMessage) {
      try {
         XMLInputFactory inputFactory = this.xmlInputFactory;
         if (inputFactory == null) {
            inputFactory = XMLInputFactory.newInstance();
            inputFactory.setProperty("javax.xml.stream.supportDTD", this.isSupportDtd());
            inputFactory.setProperty("javax.xml.stream.isSupportingExternalEntities", this.isProcessExternalEntities());
            if (!this.isProcessExternalEntities()) {
               inputFactory.setXMLResolver(NO_OP_XML_RESOLVER);
            }

            this.xmlInputFactory = inputFactory;
         }

         XMLStreamReader streamReader = inputFactory.createXMLStreamReader(body);
         return new StAXSource(streamReader);
      } catch (XMLStreamException ex) {
         throw new HttpMessageNotReadableException("Could not parse document: " + ex.getMessage(), ex, inputMessage);
      }
   }

   private StreamSource readStreamSource(InputStream body) throws IOException {
      byte[] bytes = StreamUtils.copyToByteArray(body);
      return new StreamSource(new ByteArrayInputStream(bytes));
   }

   protected @Nullable Long getContentLength(T t, @Nullable MediaType contentType) {
      if (t instanceof DOMSource) {
         try {
            SourceHttpMessageConverter.CountingOutputStream os = new SourceHttpMessageConverter.CountingOutputStream();
            this.transform(t, new StreamResult(os));
            return os.count;
         } catch (TransformerException var4) {
         }
      }

      return null;
   }

   protected void writeInternal(T t, HttpOutputMessage outputMessage) throws IOException, HttpMessageNotWritableException {
      try {
         Result result = new StreamResult(outputMessage.getBody());
         this.transform(t, result);
      } catch (TransformerException ex) {
         throw new HttpMessageNotWritableException("Could not transform [" + t + "] to output message", ex);
      }
   }

   private void transform(Source source, Result result) throws TransformerException {
      this.transformerFactory.newTransformer().transform(source, result);
   }

   public boolean canWriteRepeatedly(T t, @Nullable MediaType contentType) {
      return t instanceof DOMSource;
   }

   protected boolean supportsRepeatableWrites(T t) {
      return this.canWriteRepeatedly(t, null);
   }

   private static class CountingOutputStream extends OutputStream {
      long count = 0L;

      @Override
      public void write(int b) throws IOException {
         this.count++;
      }

      @Override
      public void write(byte[] b) throws IOException {
         this.count += b.length;
      }

      @Override
      public void write(byte[] b, int off, int len) throws IOException {
         this.count += len;
      }
   }
}
