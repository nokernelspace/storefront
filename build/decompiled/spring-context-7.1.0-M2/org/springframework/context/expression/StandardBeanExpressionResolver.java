package org.springframework.context.expression;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanExpressionException;
import org.springframework.beans.factory.config.BeanExpressionContext;
import org.springframework.beans.factory.config.BeanExpressionResolver;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.SpringProperties;
import org.springframework.core.convert.ConversionService;
import org.springframework.core.convert.support.DefaultConversionService;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.expression.spel.support.StandardTypeConverter;
import org.springframework.expression.spel.support.StandardTypeLocator;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class StandardBeanExpressionResolver implements BeanExpressionResolver {
   public static final String MAX_SPEL_EXPRESSION_LENGTH_PROPERTY_NAME = "spring.context.expression.maxLength";
   public static final String DEFAULT_EXPRESSION_PREFIX = "#{";
   public static final String DEFAULT_EXPRESSION_SUFFIX = "}";
   private String expressionPrefix = "#{";
   private String expressionSuffix = "}";
   private ExpressionParser expressionParser;
   private final Map<String, Expression> expressionCache = new ConcurrentHashMap<>(256);
   private final Map<BeanExpressionContext, StandardEvaluationContext> evaluationCache = new ConcurrentHashMap<>(8);
   private final ParserContext beanExpressionParserContext = new ParserContext() {
      public boolean isTemplate() {
         return true;
      }

      public String getExpressionPrefix() {
         return StandardBeanExpressionResolver.this.expressionPrefix;
      }

      public String getExpressionSuffix() {
         return StandardBeanExpressionResolver.this.expressionSuffix;
      }
   };

   public StandardBeanExpressionResolver() {
      this(null);
   }

   public StandardBeanExpressionResolver(@Nullable ClassLoader beanClassLoader) {
      SpelParserConfiguration parserConfig = SpelParserConfiguration.builder()
         .compilerClassLoader(beanClassLoader)
         .maximumExpressionLength(retrieveMaxExpressionLength())
         .build();
      this.expressionParser = new SpelExpressionParser(parserConfig);
   }

   public void setExpressionPrefix(String expressionPrefix) {
      Assert.hasText(expressionPrefix, "Expression prefix must not be empty");
      this.expressionPrefix = expressionPrefix;
   }

   public void setExpressionSuffix(String expressionSuffix) {
      Assert.hasText(expressionSuffix, "Expression suffix must not be empty");
      this.expressionSuffix = expressionSuffix;
   }

   public void setExpressionParser(ExpressionParser expressionParser) {
      Assert.notNull(expressionParser, "ExpressionParser must not be null");
      this.expressionParser = expressionParser;
   }

   public @Nullable Object evaluate(@Nullable String value, BeanExpressionContext beanExpressionContext) throws BeansException {
      if (!StringUtils.hasLength(value)) {
         return value;
      }

      try {
         Expression expr = this.expressionCache
            .computeIfAbsent(value, expression -> this.expressionParser.parseExpression(expression, this.beanExpressionParserContext));
         EvaluationContext evalContext = (EvaluationContext)this.evaluationCache.computeIfAbsent(beanExpressionContext, bec -> {
            ConfigurableBeanFactory beanFactory = bec.getBeanFactory();
            StandardEvaluationContext sec = new StandardEvaluationContext(bec);
            sec.addPropertyAccessor(new BeanExpressionContextAccessor());
            sec.addPropertyAccessor(new BeanFactoryAccessor());
            sec.addPropertyAccessor(new org.springframework.expression.spel.support.MapAccessor());
            sec.addPropertyAccessor(new EnvironmentAccessor());
            sec.setBeanResolver(new BeanFactoryResolver(beanFactory));
            sec.setTypeLocator(new StandardTypeLocator(beanFactory.getBeanClassLoader()));
            sec.setTypeConverter(new StandardTypeConverter(() -> {
               ConversionService cs = beanFactory.getConversionService();
               return cs != null ? cs : DefaultConversionService.getSharedInstance();
            }));
            this.customizeEvaluationContext(sec);
            return sec;
         });
         return expr.getValue(evalContext);
      } catch (Throwable ex) {
         throw new BeanExpressionException("Expression parsing failed", ex);
      }
   }

   protected void customizeEvaluationContext(StandardEvaluationContext evalContext) {
   }

   private static int retrieveMaxExpressionLength() {
      String value = SpringProperties.getProperty("spring.context.expression.maxLength");
      if (!StringUtils.hasText(value)) {
         return 10000;
      }

      try {
         int maxLength = Integer.parseInt(value.trim());
         Assert.isTrue(maxLength > 0, () -> "Value [" + maxLength + "] for system property [spring.context.expression.maxLength] must be positive");
         return maxLength;
      } catch (NumberFormatException ex) {
         throw new IllegalArgumentException("Failed to parse value for system property [spring.context.expression.maxLength]: " + ex.getMessage(), ex);
      }
   }
}
