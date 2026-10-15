package org.springframework.scheduling.config;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.beans.factory.parsing.BeanComponentDefinition;
import org.springframework.beans.factory.parsing.CompositeComponentDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.xml.BeanDefinitionParser;
import org.springframework.beans.factory.xml.ParserContext;
import org.springframework.util.StringUtils;
import org.w3c.dom.Element;

public class AnnotationDrivenBeanDefinitionParser implements BeanDefinitionParser {
   private static final String ASYNC_EXECUTION_ASPECT_CLASS_NAME = "org.springframework.scheduling.aspectj.AnnotationAsyncExecutionAspect";

   public @Nullable BeanDefinition parse(Element element, ParserContext parserContext) {
      Object source = parserContext.extractSource(element);
      CompositeComponentDefinition compDefinition = new CompositeComponentDefinition(element.getTagName(), source);
      parserContext.pushContainingComponent(compDefinition);
      BeanDefinitionRegistry registry = parserContext.getRegistry();
      String mode = element.getAttribute("mode");
      if ("aspectj".equals(mode)) {
         this.registerAsyncExecutionAspect(element, parserContext);
      } else if (registry.containsBeanDefinition("org.springframework.scheduling.config.internalAsyncAnnotationProcessor")) {
         parserContext.getReaderContext().error("Only one AsyncAnnotationBeanPostProcessor may exist within the context.", source);
      } else {
         BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(
            "org.springframework.scheduling.annotation.AsyncAnnotationBeanPostProcessor"
         );
         builder.getRawBeanDefinition().setSource(source);
         String executor = element.getAttribute("executor");
         if (StringUtils.hasText(executor)) {
            builder.addPropertyReference("executor", executor);
         }

         String exceptionHandler = element.getAttribute("exception-handler");
         if (StringUtils.hasText(exceptionHandler)) {
            builder.addPropertyReference("exceptionHandler", exceptionHandler);
         }

         if (Boolean.parseBoolean(element.getAttribute("proxy-target-class"))) {
            builder.addPropertyValue("proxyTargetClass", true);
         }

         registerPostProcessor(parserContext, builder, "org.springframework.scheduling.config.internalAsyncAnnotationProcessor");
      }

      if (registry.containsBeanDefinition("org.springframework.scheduling.config.internalScheduledAnnotationProcessor")) {
         parserContext.getReaderContext().error("Only one ScheduledAnnotationBeanPostProcessor may exist within the context.", source);
      } else {
         BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(
            "org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor"
         );
         builder.getRawBeanDefinition().setSource(source);
         String scheduler = element.getAttribute("scheduler");
         if (StringUtils.hasText(scheduler)) {
            builder.addPropertyReference("scheduler", scheduler);
         }

         registerPostProcessor(parserContext, builder, "org.springframework.scheduling.config.internalScheduledAnnotationProcessor");
      }

      parserContext.popAndRegisterContainingComponent();
      return null;
   }

   private void registerAsyncExecutionAspect(Element element, ParserContext parserContext) {
      if (!parserContext.getRegistry().containsBeanDefinition("org.springframework.scheduling.config.internalAsyncExecutionAspect")) {
         BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition("org.springframework.scheduling.aspectj.AnnotationAsyncExecutionAspect");
         builder.setFactoryMethod("aspectOf");
         String executor = element.getAttribute("executor");
         if (StringUtils.hasText(executor)) {
            builder.addPropertyReference("executor", executor);
         }

         String exceptionHandler = element.getAttribute("exception-handler");
         if (StringUtils.hasText(exceptionHandler)) {
            builder.addPropertyReference("exceptionHandler", exceptionHandler);
         }

         parserContext.registerBeanComponent(
            new BeanComponentDefinition(builder.getBeanDefinition(), "org.springframework.scheduling.config.internalAsyncExecutionAspect")
         );
      }
   }

   private static void registerPostProcessor(ParserContext parserContext, BeanDefinitionBuilder builder, String beanName) {
      builder.setRole(2);
      parserContext.getRegistry().registerBeanDefinition(beanName, builder.getBeanDefinition());
      BeanDefinitionHolder holder = new BeanDefinitionHolder(builder.getBeanDefinition(), beanName);
      parserContext.registerComponent(new BeanComponentDefinition(holder));
   }
}
