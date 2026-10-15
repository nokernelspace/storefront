package org.springframework.scheduling.config;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

public class SchedulerBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {
   protected String getBeanClassName(Element element) {
      return "org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler";
   }

   protected void doParse(Element element, BeanDefinitionBuilder builder) {
      String poolSize = element.getAttribute("pool-size");
      if (StringUtils.hasText(poolSize)) {
         builder.addPropertyValue("poolSize", poolSize);
      }

      builder.setRole(2);
   }
}
