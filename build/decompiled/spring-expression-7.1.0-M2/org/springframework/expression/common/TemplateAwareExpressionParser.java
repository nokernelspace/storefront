package org.springframework.expression.common;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParseException;
import org.springframework.expression.ParserContext;
import org.springframework.util.Assert;

public abstract class TemplateAwareExpressionParser implements ExpressionParser {
   private static final Expression[] EMPTY_EXPRESSION_ARRAY = new Expression[0];

   @Override
   public Expression parseExpression(String expressionString) throws ParseException {
      return this.parseExpression(expressionString, null);
   }

   @Override
   public Expression parseExpression(String expressionString, @Nullable ParserContext context) throws ParseException {
      if (context != null && context.isTemplate()) {
         Assert.notNull(expressionString, "'expressionString' must not be null");
         return this.parseTemplate(expressionString, context);
      } else {
         Assert.hasText(expressionString, "'expressionString' must not be null or blank");
         return this.doParseExpression(expressionString, context);
      }
   }

   private Expression parseTemplate(String expressionString, ParserContext context) throws ParseException {
      if (expressionString.isEmpty()) {
         return new LiteralExpression("");
      }

      Expression[] expressions = this.parseExpressions(expressionString, context);
      return expressions.length == 1 ? expressions[0] : new CompositeStringExpression(expressionString, expressions);
   }

   private Expression[] parseExpressions(String expressionString, ParserContext context) throws ParseException {
      List<Expression> expressions = new ArrayList<>();
      String prefix = context.getExpressionPrefix();
      String suffix = context.getExpressionSuffix();
      int startIdx = 0;

      while (startIdx < expressionString.length()) {
         int prefixIndex = expressionString.indexOf(prefix, startIdx);
         if (prefixIndex >= startIdx) {
            if (prefixIndex > startIdx) {
               expressions.add(new LiteralExpression(expressionString.substring(startIdx, prefixIndex)));
            }

            int afterPrefixIndex = prefixIndex + prefix.length();
            int suffixIndex = this.skipToCorrectEndSuffix(suffix, expressionString, afterPrefixIndex);
            if (suffixIndex == -1) {
               throw new ParseException(
                  expressionString,
                  prefixIndex,
                  "No ending suffix '" + suffix + "' for expression starting at character " + prefixIndex + ": " + expressionString.substring(prefixIndex)
               );
            }

            if (suffixIndex == afterPrefixIndex) {
               throw new ParseException(
                  expressionString, prefixIndex, "No expression defined within delimiter '" + prefix + suffix + "' at character " + prefixIndex
               );
            }

            String expr = expressionString.substring(prefixIndex + prefix.length(), suffixIndex);
            expr = expr.trim();
            if (expr.isEmpty()) {
               throw new ParseException(
                  expressionString, prefixIndex, "No expression defined within delimiter '" + prefix + suffix + "' at character " + prefixIndex
               );
            }

            expressions.add(this.doParseExpression(expr, context));
            startIdx = suffixIndex + suffix.length();
         } else {
            expressions.add(new LiteralExpression(expressionString.substring(startIdx)));
            startIdx = expressionString.length();
         }
      }

      return expressions.toArray(EMPTY_EXPRESSION_ARRAY);
   }

   private boolean isSuffixHere(String expressionString, int pos, String suffix) {
      int suffixPosition = 0;

      for (int i = 0; i < suffix.length() && pos < expressionString.length(); i++) {
         if (expressionString.charAt(pos++) != suffix.charAt(suffixPosition++)) {
            return false;
         }
      }

      return suffixPosition == suffix.length();
   }

   private int skipToCorrectEndSuffix(String suffix, String expressionString, int afterPrefixIndex) throws ParseException {
      int pos = afterPrefixIndex;
      int maxlen = expressionString.length();
      int nextSuffix = expressionString.indexOf(suffix, afterPrefixIndex);
      if (nextSuffix == -1) {
         return -1;
      }

      Deque<TemplateAwareExpressionParser.Bracket> stack;
      for (stack = new ArrayDeque<>(); pos < maxlen && (!this.isSuffixHere(expressionString, pos, suffix) || !stack.isEmpty()); pos++) {
         char ch = expressionString.charAt(pos);
         switch (ch) {
            case '"':
            case '\'':
               int endLiteral = expressionString.indexOf(ch, pos + 1);
               if (endLiteral == -1) {
                  throw new ParseException(expressionString, pos, "Found non terminating string literal starting at position " + pos);
               }

               pos = endLiteral;
               break;
            case '(':
            case '[':
            case '{':
               stack.push(new TemplateAwareExpressionParser.Bracket(ch, pos));
               break;
            case ')':
            case ']':
            case '}':
               if (stack.isEmpty()) {
                  throw new ParseException(
                     expressionString,
                     pos,
                     "Found closing '"
                        + ch
                        + "' at position "
                        + pos
                        + " without an opening '"
                        + TemplateAwareExpressionParser.Bracket.theOpenBracketFor(ch)
                        + "'"
                  );
               }

               TemplateAwareExpressionParser.Bracket p = stack.pop();
               if (!p.compatibleWithCloseBracket(ch)) {
                  throw new ParseException(
                     expressionString,
                     pos,
                     "Found closing '" + ch + "' at position " + pos + " but most recent opening is '" + p.bracket + "' at position " + p.pos
                  );
               }
         }
      }

      if (!stack.isEmpty()) {
         TemplateAwareExpressionParser.Bracket p = stack.pop();
         throw new ParseException(
            expressionString,
            p.pos,
            "Missing closing '" + TemplateAwareExpressionParser.Bracket.theCloseBracketFor(p.bracket) + "' for '" + p.bracket + "' at position " + p.pos
         );
      } else {
         return !this.isSuffixHere(expressionString, pos, suffix) ? -1 : pos;
      }
   }

   protected abstract Expression doParseExpression(String expressionString, @Nullable ParserContext context) throws ParseException;

   private record Bracket(char bracket, int pos) {
      boolean compatibleWithCloseBracket(char closeBracket) {
         return switch (this.bracket) {
            case '[' -> closeBracket == ']';
            case '{' -> closeBracket == '}';
            default -> closeBracket == ')';
         };
      }

      static char theOpenBracketFor(char closeBracket) {
         return switch (closeBracket) {
            case ']' -> '[';
            case '}' -> '{';
            default -> '(';
         };
      }

      static char theCloseBracketFor(char openBracket) {
         return switch (openBracket) {
            case '[' -> ']';
            case '{' -> '}';
            default -> ')';
         };
      }
   }
}
