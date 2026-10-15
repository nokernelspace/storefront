package org.springframework.boot.diagnostics.analyzer;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.diagnostics.FailureAnalysis;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

class NoUniqueBeanDefinitionFailureAnalyzer extends AbstractInjectionFailureAnalyzer<NoUniqueBeanDefinitionException> {
   private final ConfigurableBeanFactory beanFactory;

   NoUniqueBeanDefinitionFailureAnalyzer(BeanFactory beanFactory) {
      Assert.isInstanceOf(ConfigurableBeanFactory.class, beanFactory);
      this.beanFactory = (ConfigurableBeanFactory)beanFactory;
   }

   protected @Nullable FailureAnalysis analyze(Throwable rootFailure, NoUniqueBeanDefinitionException cause, @Nullable String description) {
      String[] beanNames = this.extractBeanNames(cause);
      if (beanNames == null) {
         return null;
      }

      StringBuilder message = new StringBuilder();
      message.append(String.format("%s required a single bean, but %d were found:%n", description != null ? description : "A component", beanNames.length));

      for (String beanName : beanNames) {
         this.buildMessage(message, beanName);
      }

      MissingParameterNamesFailureAnalyzer.appendPossibility(message);
      StringBuilder action = new StringBuilder(
         "Consider marking one of the beans as @Primary, updating the consumer to accept multiple beans, or using @Qualifier to identify the bean that should be consumed"
      );
      action.append(
         "%n%n%s"
            .formatted(
               "Ensure that your compiler is configured to use the '-parameters' flag.\nYou may need to update both your build tool settings as well as your IDE.\n(See https://github.com/spring-projects/spring-framework/wiki/Spring-Framework-6.1-Release-Notes#parameter-name-retention)\n"
            )
      );
      return new FailureAnalysis(message.toString(), action.toString(), cause);
   }

   private void buildMessage(StringBuilder message, String beanName) {
      try {
         BeanDefinition definition = this.beanFactory.getMergedBeanDefinition(beanName);
         message.append(this.getDefinitionDescription(beanName, definition));
      } catch (NoSuchBeanDefinitionException ex) {
         message.append(String.format("\t- %s: a programmatically registered singleton%n", beanName));
      }
   }

   private String getDefinitionDescription(String beanName, BeanDefinition definition) {
      return StringUtils.hasText(definition.getFactoryMethodName())
         ? String.format("\t- %s: defined by method '%s' in %s%n", beanName, definition.getFactoryMethodName(), this.getResourceDescription(definition))
         : String.format("\t- %s: defined in %s%n", beanName, this.getResourceDescription(definition));
   }

   private String getResourceDescription(BeanDefinition definition) {
      String resourceDescription = definition.getResourceDescription();
      return resourceDescription != null ? resourceDescription : "unknown location";
   }

   private String @Nullable [] extractBeanNames(NoUniqueBeanDefinitionException cause) {
      String message = cause.getMessage();
      if (message == null) {
         message = "";
      }

      return message.contains("but found") ? StringUtils.commaDelimitedListToStringArray(message.substring(message.lastIndexOf(58) + 1).trim()) : null;
   }
}
