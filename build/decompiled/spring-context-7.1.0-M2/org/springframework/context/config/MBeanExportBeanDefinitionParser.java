package org.springframework.context.config;

import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.jmx.export.annotation.AnnotationMBeanExporter;
import org.springframework.jmx.support.RegistrationPolicy;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

class MBeanExportBeanDefinitionParser extends AbstractBeanDefinitionParser {
   private static final String MBEAN_EXPORTER_BEAN_NAME = "mbeanExporter";
   private static final String DEFAULT_DOMAIN_ATTRIBUTE = "default-domain";
   private static final String SERVER_ATTRIBUTE = "server";
   private static final String REGISTRATION_ATTRIBUTE = "registration";
   private static final String REGISTRATION_IGNORE_EXISTING = "ignoreExisting";
   private static final String REGISTRATION_REPLACE_EXISTING = "replaceExisting";

   protected String resolveId(Element element, AbstractBeanDefinition definition, ParserContext parserContext) {
      return "mbeanExporter";
   }

   protected AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
      BeanDefinitionBuilder builder = BeanDefinitionBuilder.rootBeanDefinition(AnnotationMBeanExporter.class);
      builder.setRole(2);
      builder.getRawBeanDefinition().setSource(parserContext.extractSource(element));
      String defaultDomain = element.getAttribute("default-domain");
      if (StringUtils.hasText(defaultDomain)) {
         builder.addPropertyValue("defaultDomain", defaultDomain);
      }

      String serverBeanName = element.getAttribute("server");
      if (StringUtils.hasText(serverBeanName)) {
         builder.addPropertyReference("server", serverBeanName);
      }

      String registration = element.getAttribute("registration");
      RegistrationPolicy registrationPolicy = RegistrationPolicy.FAIL_ON_EXISTING;
      if ("ignoreExisting".equals(registration)) {
         registrationPolicy = RegistrationPolicy.IGNORE_EXISTING;
      } else if ("replaceExisting".equals(registration)) {
         registrationPolicy = RegistrationPolicy.REPLACE_EXISTING;
      }

      builder.addPropertyValue("registrationPolicy", registrationPolicy);
      return builder.getBeanDefinition();
   }
}
