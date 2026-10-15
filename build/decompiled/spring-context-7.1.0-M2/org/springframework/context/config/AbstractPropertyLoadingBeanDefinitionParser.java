package org.springframework.context.config;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

abstract class AbstractPropertyLoadingBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {
   protected boolean shouldGenerateId() {
      return true;
   }

   protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
      String location = element.getAttribute("location");
      if (StringUtils.hasLength(location)) {
         location = parserContext.getReaderContext().getEnvironment().resolvePlaceholders(location);
         String[] locations = StringUtils.commaDelimitedListToStringArray(location);
         builder.addPropertyValue("locations", locations);
      }

      String propertiesRef = element.getAttribute("properties-ref");
      if (StringUtils.hasLength(propertiesRef)) {
         builder.addPropertyReference("properties", propertiesRef);
      }

      String fileEncoding = element.getAttribute("file-encoding");
      if (StringUtils.hasLength(fileEncoding)) {
         builder.addPropertyValue("fileEncoding", fileEncoding);
      }

      String order = element.getAttribute("order");
      if (StringUtils.hasLength(order)) {
         builder.addPropertyValue("order", Integer.valueOf(order));
      }

      builder.addPropertyValue("ignoreResourceNotFound", Boolean.valueOf(element.getAttribute("ignore-resource-not-found")));
      builder.addPropertyValue("localOverride", Boolean.valueOf(element.getAttribute("local-override")));
      builder.setRole(2);
   }
}
