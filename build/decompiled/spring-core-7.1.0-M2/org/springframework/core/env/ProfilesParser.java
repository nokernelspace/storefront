package org.springframework.core.env;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

final class ProfilesParser {
   private ProfilesParser() {
   }

   static Profiles parse(String... expressions) {
      Assert.notEmpty(expressions, "Must specify at least one profile expression");
      Profiles[] parsed = new Profiles[expressions.length];

      for (int i = 0; i < expressions.length; i++) {
         parsed[i] = parseExpression(expressions[i]);
      }

      return new ProfilesParser.ParsedProfiles(expressions, parsed);
   }

   private static Profiles parseExpression(String expression) {
      Assert.hasText(expression, () -> "Invalid profile expression [" + expression + "]: must contain text");
      StringTokenizer tokens = new StringTokenizer(expression, "()&|!", true);
      return parseTokens(expression, tokens);
   }

   private static Profiles parseTokens(String expression, StringTokenizer tokens) {
      return parseTokens(expression, tokens, ProfilesParser.Context.NONE);
   }

   private static Profiles parseTokens(String expression, StringTokenizer tokens, ProfilesParser.Context context) {
      List<Profiles> elements = new ArrayList<>();
      ProfilesParser.Operator operator = null;

      while (tokens.hasMoreTokens()) {
         String token = tokens.nextToken().trim();
         if (!token.isEmpty()) {
            switch (token) {
               case "(":
                  Profiles contents = parseTokens(expression, tokens, ProfilesParser.Context.PARENTHESIS);
                  if (context == ProfilesParser.Context.NEGATE) {
                     return contents;
                  }

                  elements.add(contents);
                  break;
               case "&":
                  assertWellFormed(expression, operator == null || operator == ProfilesParser.Operator.AND);
                  operator = ProfilesParser.Operator.AND;
                  break;
               case "|":
                  assertWellFormed(expression, operator == null || operator == ProfilesParser.Operator.OR);
                  operator = ProfilesParser.Operator.OR;
                  break;
               case "!":
                  elements.add(not(parseTokens(expression, tokens, ProfilesParser.Context.NEGATE)));
                  break;
               case ")":
                  if (context == ProfilesParser.Context.PARENTHESIS) {
                     return merge(expression, elements, operator);
                  }

                  assertWellFormed(expression, false);
                  break;
               default:
                  Profiles value = equals(token);
                  if (context == ProfilesParser.Context.NEGATE) {
                     return value;
                  }

                  elements.add(value);
            }
         }
      }

      assertWellFormed(expression, context != ProfilesParser.Context.PARENTHESIS);
      return merge(expression, elements, operator);
   }

   private static Profiles merge(String expression, List<Profiles> elements, ProfilesParser.@Nullable Operator operator) {
      assertWellFormed(expression, !elements.isEmpty());
      if (elements.size() == 1) {
         return elements.get(0);
      }

      Profiles[] profiles = elements.toArray(new Profiles[0]);
      return operator == ProfilesParser.Operator.AND ? and(profiles) : or(profiles);
   }

   private static void assertWellFormed(String expression, boolean wellFormed) {
      Assert.isTrue(wellFormed, () -> "Malformed profile expression [" + expression + "]");
   }

   private static Profiles or(Profiles... profiles) {
      return activeProfile -> Arrays.stream(profiles).anyMatch(isMatch(activeProfile));
   }

   private static Profiles and(Profiles... profiles) {
      return activeProfile -> Arrays.stream(profiles).allMatch(isMatch(activeProfile));
   }

   private static Profiles not(Profiles profiles) {
      return activeProfile -> !profiles.matches(activeProfile);
   }

   private static Profiles equals(String profile) {
      return activeProfile -> activeProfile.test(profile);
   }

   private static Predicate<Profiles> isMatch(Predicate<String> activeProfiles) {
      return profiles -> profiles.matches(activeProfiles);
   }

   private enum Context {
      NONE,
      NEGATE,
      PARENTHESIS;
   }

   private enum Operator {
      AND,
      OR;
   }

   private static class ParsedProfiles implements Profiles {
      private final Set<String> expressions = new LinkedHashSet<>();
      private final Profiles[] parsed;

      ParsedProfiles(String[] expressions, Profiles[] parsed) {
         Collections.addAll(this.expressions, expressions);
         this.parsed = parsed;
      }

      @Override
      public boolean matches(Predicate<String> activeProfiles) {
         for (Profiles candidate : this.parsed) {
            if (candidate.matches(activeProfiles)) {
               return true;
            }
         }

         return false;
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other || other instanceof ProfilesParser.ParsedProfiles that && this.expressions.equals(that.expressions);
      }

      @Override
      public int hashCode() {
         return this.expressions.hashCode();
      }

      @Override
      public String toString() {
         return this.expressions.size() == 1
            ? this.expressions.iterator().next()
            : this.expressions.stream().map(this::wrap).collect(Collectors.joining(" | "));
      }

      private String wrap(String str) {
         return "(" + str + ")";
      }
   }
}
