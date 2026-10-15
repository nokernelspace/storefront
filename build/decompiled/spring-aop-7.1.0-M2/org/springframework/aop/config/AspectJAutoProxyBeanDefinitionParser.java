package org.springframework.aop.config;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.TypedStringValue;
import org.springframework.beans.factory.support.ManagedList;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

class AspectJAutoProxyBeanDefinitionParser implements BeanDefinitionParser {
   public @Nullable BeanDefinition parse(Element element, ParserContext parserContext) {
      AopNamespaceUtils.registerAspectJAnnotationAutoProxyCreatorIfNecessary(parserContext, element);
      this.extendBeanDefinition(element, parserContext);
      return null;
   }

   private void extendBeanDefinition(Element element, ParserContext parserContext) {
      BeanDefinition beanDef = parserContext.getRegistry().getBeanDefinition("org.springframework.aop.config.internalAutoProxyCreator");
      if (element.hasChildNodes()) {
         this.addIncludePatterns(element, parserContext, beanDef);
      }
   }

   private void addIncludePatterns(Element element, ParserContext parserContext, BeanDefinition beanDef) {
      ManagedList<TypedStringValue> includePatterns = new ManagedList();
      NodeList childNodes = element.getChildNodes();

      for (int i = 0; i < childNodes.getLength(); i++) {
         if (childNodes.item(i) instanceof Element includeElement) {
            TypedStringValue valueHolder = new TypedStringValue(includeElement.getAttribute("name"));
            valueHolder.setSource(parserContext.extractSource(includeElement));
            includePatterns.add(valueHolder);
         }
      }

      if (!includePatterns.isEmpty()) {
         includePatterns.setSource(parserContext.extractSource(element));
         beanDef.getPropertyValues().add("includePatterns", includePatterns);
      }
   }
}
