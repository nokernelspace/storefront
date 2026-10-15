package org.springframework.beans.factory.xml;

import org.springframework.beans.factory.BeanDefinitionStoreException;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class XmlBeanDefinitionStoreException extends BeanDefinitionStoreException {
   public XmlBeanDefinitionStoreException(String resourceDescription, String msg, SAXException cause) {
      super(resourceDescription, msg, cause);
   }

   public int getLineNumber() {
      return this.getCause() instanceof SAXParseException parseEx ? parseEx.getLineNumber() : -1;
   }
}
