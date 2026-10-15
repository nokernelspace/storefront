package org.springframework.context.config;

import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.AbstractBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.jmx.support.MBeanServerFactoryBean;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

class MBeanServerBeanDefinitionParser extends AbstractBeanDefinitionParser {
   private static final String MBEAN_SERVER_BEAN_NAME = "mbeanServer";
   private static final String AGENT_ID_ATTRIBUTE = "agent-id";

   protected String resolveId(Element element, AbstractBeanDefinition definition, ParserContext parserContext) {
      String id = element.getAttribute("id");
      return StringUtils.hasText(id) ? id : "mbeanServer";
   }

   protected AbstractBeanDefinition parseInternal(Element element, ParserContext parserContext) {
      String agentId = element.getAttribute("agent-id");
      if (StringUtils.hasText(agentId)) {
         RootBeanDefinition bd = new RootBeanDefinition(MBeanServerFactoryBean.class);
         bd.getPropertyValues().add("agentId", agentId);
         return bd;
      } else {
         RootBeanDefinition bd = new RootBeanDefinition(MBeanServerFactoryBean.class);
         bd.getPropertyValues().add("locateExistingServerIfPossible", Boolean.TRUE);
         bd.setRole(2);
         bd.setSource(parserContext.extractSource(element));
         return bd;
      }
   }
}
