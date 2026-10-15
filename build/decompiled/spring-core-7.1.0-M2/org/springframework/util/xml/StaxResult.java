package org.springframework.util.xml;

import javax.xml.stream.XMLEventWriter;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.transform.sax.SAXResult;
import org.jspecify.annotations.Nullable;
import org.xml.sax.ContentHandler;
import org.xml.sax.ext.LexicalHandler;

class StaxResult extends SAXResult {
   private @Nullable XMLEventWriter eventWriter;
   private @Nullable XMLStreamWriter streamWriter;

   public StaxResult(XMLEventWriter eventWriter) {
      StaxEventHandler handler = new StaxEventHandler(eventWriter);
      super.setHandler(handler);
      super.setLexicalHandler(handler);
      this.eventWriter = eventWriter;
   }

   public StaxResult(XMLStreamWriter streamWriter) {
      StaxStreamHandler handler = new StaxStreamHandler(streamWriter);
      super.setHandler(handler);
      super.setLexicalHandler(handler);
      this.streamWriter = streamWriter;
   }

   public @Nullable XMLEventWriter getXMLEventWriter() {
      return this.eventWriter;
   }

   public @Nullable XMLStreamWriter getXMLStreamWriter() {
      return this.streamWriter;
   }

   @Override
   public void setHandler(ContentHandler handler) {
      throw new UnsupportedOperationException("setHandler is not supported");
   }

   @Override
   public void setLexicalHandler(LexicalHandler handler) {
      throw new UnsupportedOperationException("setLexicalHandler is not supported");
   }
}
