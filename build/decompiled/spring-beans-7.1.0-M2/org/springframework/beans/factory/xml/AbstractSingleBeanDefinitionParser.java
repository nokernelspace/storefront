package org.springframework.beans.factory.xml;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.w3c.dom.Element;

public abstract class AbstractSingleBeanDefinitionParser extends AbstractBeanDefinitionParser {
   @Override
   protected final AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
      BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition();
      String parentName = this.getParentName(element);
      if (parentName != null) {
         builder.getRawBeanDefinition().setParentName(parentName);
      }

      Class<?> beanClass = this.getBeanClass(element);
      if (beanClass != null) {
         builder.getRawBeanDefinition().setBeanClass(beanClass);
      } else {
         String beanClassName = this.getBeanClassName(element);
         if (beanClassName != null) {
            builder.getRawBeanDefinition().setBeanClassName(beanClassName);
         }
      }

      builder.getRawBeanDefinition().setSource(parserContext.extractSource(element));
      BeanDefinition containingBd = parserContext.getContainingBeanDefinition();
      if (containingBd != null) {
         builder.setScope(containingBd.getScope());
      }

      if (parserContext.isDefaultLazyInit()) {
         builder.setLazyInit(true);
      }

      this.doParse(element, parserContext, builder);
      return builder.getBeanDefinition();
   }

   protected @Nullable String getParentName(Element element) {
      return null;
   }

   protected @Nullable Class<?> getBeanClass(Element element) {
      return null;
   }

   protected @Nullable String getBeanClassName(Element element) {
      return null;
   }

   protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
      this.doParse(element, builder);
   }

   protected void doParse(Element element, BeanDefinitionBuilder builder) {
   }
}
