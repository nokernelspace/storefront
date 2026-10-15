package org.springframework.beans.factory.xml;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.core.Conventions;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

public class SimplePropertyNamespaceHandler implements NamespaceHandler {
   private static final String REF_SUFFIX = "-ref";

   @Override
   public void init() {
   }

   @Override
   public @Nullable BeanDefinition parse(Element element, ParserContext parserContext) {
      parserContext.getReaderContext().error("Class [" + this.getClass().getName() + "] does not support custom elements.", element);
      return null;
   }

   @Override
   public BeanDefinitionHolder decorate(Node node, BeanDefinitionHolder definition, ParserContext parserContext) {
      if (node instanceof Attr attr) {
         String propertyName = parserContext.getDelegate().getLocalName(attr);
         String propertyValue = attr.getValue();
         MutablePropertyValues pvs = definition.getBeanDefinition().getPropertyValues();
         if (pvs.contains(propertyName)) {
            parserContext.getReaderContext()
               .error(
                  "Property '" + propertyName + "' is already defined using both <property> and inline syntax. Only one approach may be used per property.",
                  attr
               );
         }

         if (propertyName.endsWith("-ref")) {
            propertyName = propertyName.substring(0, propertyName.length() - "-ref".length());
            pvs.add(Conventions.attributeNameToPropertyName(propertyName), new RuntimeBeanReference(propertyValue));
         } else {
            pvs.add(Conventions.attributeNameToPropertyName(propertyName), propertyValue);
         }
      }

      return definition;
   }
}
