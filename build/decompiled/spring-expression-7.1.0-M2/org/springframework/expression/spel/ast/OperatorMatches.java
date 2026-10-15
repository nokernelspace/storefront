package org.springframework.expression.spel.ast;

import java.util.concurrent.ConcurrentMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import org.springframework.expression.EvaluationException;
import org.springframework.expression.spel.ExpressionState;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.expression.spel.SpelMessage;
import org.springframework.expression.spel.support.BooleanTypedValue;
import org.springframework.util.ConcurrentLruCache;

public class OperatorMatches extends Operator {
   public static final int MAX_PATTERN_CACHE_SIZE = 256;
   private static final int PATTERN_ACCESS_THRESHOLD = 1000000;
   private static final int MAX_REGEX_LENGTH = 1000;
   private final ConcurrentLruCache<String, Pattern> patternCache;

   @Deprecated(since = "5.2.23", forRemoval = true)
   public OperatorMatches(int startPos, int endPos, SpelNodeImpl... operands) {
      this(new ConcurrentLruCache(256, Pattern::compile), startPos, endPos, operands);
   }

   @Deprecated(since = "6.2.19", forRemoval = true)
   public OperatorMatches(ConcurrentMap<String, Pattern> patternCacheMap, int startPos, int endPos, SpelNodeImpl... operands) {
      this(startPos, endPos, operands);
   }

   public OperatorMatches(ConcurrentLruCache<String, Pattern> patternCache, int startPos, int endPos, SpelNodeImpl... operands) {
      super("matches", startPos, endPos, operands);
      this.patternCache = patternCache;
   }

   public BooleanTypedValue getValueInternal(ExpressionState state) throws EvaluationException {
      SpelNodeImpl leftOp = this.getLeftOperand();
      SpelNodeImpl rightOp = this.getRightOperand();
      String input = leftOp.getValue(state, String.class);
      if (input == null) {
         throw new SpelEvaluationException(leftOp.getStartPosition(), SpelMessage.INVALID_FIRST_OPERAND_FOR_MATCHES_OPERATOR, null);
      }

      Object right = rightOp.getValue(state);
      if (right instanceof String regex) {
         if (regex.length() > 1000) {
            throw new SpelEvaluationException(rightOp.getStartPosition(), SpelMessage.MAX_REGEX_LENGTH_EXCEEDED, 1000);
         }

         state.trackOperation();

         try {
            Pattern pattern = (Pattern)this.patternCache.get(regex);
            Matcher matcher = pattern.matcher(new OperatorMatches.MatcherInput(input, new OperatorMatches.AccessCount()));
            return BooleanTypedValue.forValue(matcher.matches());
         } catch (PatternSyntaxException ex) {
            throw new SpelEvaluationException(rightOp.getStartPosition(), ex, SpelMessage.INVALID_PATTERN, right);
         } catch (IllegalStateException ex) {
            throw new SpelEvaluationException(rightOp.getStartPosition(), ex, SpelMessage.FLAWED_PATTERN, right);
         }
      } else {
         throw new SpelEvaluationException(rightOp.getStartPosition(), SpelMessage.INVALID_SECOND_OPERAND_FOR_MATCHES_OPERATOR, right);
      }
   }

   private static class AccessCount {
      private int count;

      public void check() throws IllegalStateException {
         if (this.count++ > 1000000) {
            throw new IllegalStateException("Pattern access threshold exceeded");
         }
      }
   }

   private static class MatcherInput implements CharSequence {
      private final CharSequence value;
      private final OperatorMatches.AccessCount access;

      public MatcherInput(CharSequence value, OperatorMatches.AccessCount access) {
         this.value = value;
         this.access = access;
      }

      @Override
      public char charAt(int index) {
         this.access.check();
         return this.value.charAt(index);
      }

      @Override
      public CharSequence subSequence(int start, int end) {
         return new OperatorMatches.MatcherInput(this.value.subSequence(start, end), this.access);
      }

      @Override
      public int length() {
         return this.value.length();
      }

      @Override
      public String toString() {
         return this.value.toString();
      }
   }
}
