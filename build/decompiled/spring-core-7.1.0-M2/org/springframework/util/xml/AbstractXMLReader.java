package org.springframework.util.xml;

import org.jspecify.annotations.Nullable;
import org.xml.sax.ContentHandler;
import org.xml.sax.DTDHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.LexicalHandler;

abstract class AbstractXMLReader implements XMLReader {
   private @Nullable DTDHandler dtdHandler;
   private @Nullable ContentHandler contentHandler;
   private @Nullable EntityResolver entityResolver;
   private @Nullable ErrorHandler errorHandler;
   private @Nullable LexicalHandler lexicalHandler;

   @Override
   public void setContentHandler(@Nullable ContentHandler contentHandler) {
      this.contentHandler = contentHandler;
   }

   @Override
   public @Nullable ContentHandler getContentHandler() {
      return this.contentHandler;
   }

   @Override
   public void setDTDHandler(@Nullable DTDHandler dtdHandler) {
      this.dtdHandler = dtdHandler;
   }

   @Override
   public @Nullable DTDHandler getDTDHandler() {
      return this.dtdHandler;
   }

   @Override
   public void setEntityResolver(@Nullable EntityResolver entityResolver) {
      this.entityResolver = entityResolver;
   }

   @Override
   public @Nullable EntityResolver getEntityResolver() {
      return this.entityResolver;
   }

   @Override
   public void setErrorHandler(@Nullable ErrorHandler errorHandler) {
      this.errorHandler = errorHandler;
   }

   @Override
   public @Nullable ErrorHandler getErrorHandler() {
      return this.errorHandler;
   }

   protected @Nullable LexicalHandler getLexicalHandler() {
      return this.lexicalHandler;
   }

   @Override
   public boolean getFeature(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
      if (name.startsWith("http://xml.org/sax/features/")) {
         return false;
      } else {
         throw new SAXNotRecognizedException(name);
      }
   }

   @Override
   public void setFeature(String name, boolean value) throws SAXNotRecognizedException, SAXNotSupportedException {
      if (name.startsWith("http://xml.org/sax/features/")) {
         if (value) {
            throw new SAXNotSupportedException(name);
         }
      } else {
         throw new SAXNotRecognizedException(name);
      }
   }

   @Override
   public @Nullable Object getProperty(String name) throws SAXNotRecognizedException, SAXNotSupportedException {
      if ("http://xml.org/sax/properties/lexical-handler".equals(name)) {
         return this.lexicalHandler;
      } else {
         throw new SAXNotRecognizedException(name);
      }
   }

   @Override
   public void setProperty(String name, Object value) throws SAXNotRecognizedException, SAXNotSupportedException {
      if ("http://xml.org/sax/properties/lexical-handler".equals(name)) {
         this.lexicalHandler = (LexicalHandler)value;
      } else {
         throw new SAXNotRecognizedException(name);
      }
   }
}
