package org.springframework.expression.spel.standard;

import org.jspecify.annotations.Nullable;
import org.springframework.expression.ParseException;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateAwareExpressionParser;
import org.springframework.expression.spel.SpelParserConfiguration;
import org.springframework.util.Assert;

public class SpelExpressionParser extends TemplateAwareExpressionParser {
   private final SpelParserConfiguration configuration;

   public SpelExpressionParser() {
      this.configuration = SpelParserConfiguration.withDefaults();
   }

   public SpelExpressionParser(SpelParserConfiguration configuration) {
      Assert.notNull(configuration, "SpelParserConfiguration must not be null");
      this.configuration = configuration;
   }

   public SpelExpression parseRaw(String expressionString) throws ParseException {
      Assert.hasText(expressionString, "'expressionString' must not be null or blank");
      return this.doParseExpression(expressionString, null);
   }

   protected SpelExpression doParseExpression(String expressionString, @Nullable ParserContext context) throws ParseException {
      return new InternalSpelExpressionParser(this.configuration).doParseExpression(expressionString, context);
   }
}
