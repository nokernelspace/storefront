package org.springframework.ejb.config;

import org.springframework.beans.factory.config.RuntimeBeanReference;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSimpleBeanDefinitionParser;
import org.springframework.util.StringUtils;
import org.springframework.util.xml.DomUtils;
import org.w3c.dom.Element;

abstract class AbstractJndiLocatingBeanDefinitionParser extends AbstractSimpleBeanDefinitionParser {
   public static final String ENVIRONMENT = "environment";
   public static final String ENVIRONMENT_REF = "environment-ref";
   public static final String JNDI_ENVIRONMENT = "jndiEnvironment";

   protected boolean isEligibleAttribute(String attributeName) {
      return super.isEligibleAttribute(attributeName) && !"environment-ref".equals(attributeName) && !"lazy-init".equals(attributeName);
   }

   protected void postProcess(BeanDefinitionBuilder definitionBuilder, Element element) {
      Object envValue = DomUtils.getChildElementValueByTagName(element, "environment");
      if (envValue != null) {
         definitionBuilder.addPropertyValue("jndiEnvironment", envValue);
      } else {
         String envRef = element.getAttribute("environment-ref");
         if (StringUtils.hasLength(envRef)) {
            definitionBuilder.addPropertyValue("jndiEnvironment", new RuntimeBeanReference(envRef));
         }
      }

      String lazyInit = element.getAttribute("lazy-init");
      if (StringUtils.hasText(lazyInit) && !"default".equals(lazyInit)) {
         definitionBuilder.setLazyInit("true".equals(lazyInit));
      }
   }
}
