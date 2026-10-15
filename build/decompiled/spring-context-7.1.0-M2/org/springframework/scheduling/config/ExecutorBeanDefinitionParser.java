package org.springframework.scheduling.config;

import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.xml.AbstractSingleBeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

public class ExecutorBeanDefinitionParser extends AbstractSingleBeanDefinitionParser {
   protected String getBeanClassName(Element element) {
      return "org.springframework.scheduling.config.TaskExecutorFactoryBean";
   }

   protected void doParse(Element element, ParserContext parserContext, BeanDefinitionBuilder builder) {
      String keepAliveSeconds = element.getAttribute("keep-alive");
      if (StringUtils.hasText(keepAliveSeconds)) {
         builder.addPropertyValue("keepAliveSeconds", keepAliveSeconds);
      }

      String queueCapacity = element.getAttribute("queue-capacity");
      if (StringUtils.hasText(queueCapacity)) {
         builder.addPropertyValue("queueCapacity", queueCapacity);
      }

      this.configureRejectionPolicy(element, builder);
      String poolSize = element.getAttribute("pool-size");
      if (StringUtils.hasText(poolSize)) {
         builder.addPropertyValue("poolSize", poolSize);
      }

      builder.setRole(2);
   }

   private void configureRejectionPolicy(Element element, BeanDefinitionBuilder builder) {
      String rejectionPolicy = element.getAttribute("rejection-policy");
      if (StringUtils.hasText(rejectionPolicy)) {
         String prefix = "java.util.concurrent.ThreadPoolExecutor.";

         String policyClassName = switch (rejectionPolicy) {
            case "ABORT" -> prefix + "AbortPolicy";
            case "CALLER_RUNS" -> prefix + "CallerRunsPolicy";
            case "DISCARD" -> prefix + "DiscardPolicy";
            case "DISCARD_OLDEST" -> prefix + "DiscardOldestPolicy";
            default -> rejectionPolicy;
         };
         builder.addPropertyValue("rejectedExecutionHandler", new RootBeanDefinition(policyClassName));
      }
   }
}
