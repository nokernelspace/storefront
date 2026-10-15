package org.springframework.beans.factory.xml;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanDefinition;
import org.w3c.dom.Element;

public interface BeanDefinitionParser {
   @Nullable BeanDefinition parse(Element element, ParserContext parserContext);
}
