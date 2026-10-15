package org.springframework.web.util.pattern;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.server.PathContainer;

class RegexPathElement extends PathElement {
   private static final Pattern GLOB_PATTERN = Pattern.compile("\\?|\\*|\\{((?:\\{[^/]+?\\}|[^/{}]|\\\\[{}])+?)\\}");
   private static final String DEFAULT_VARIABLE_PATTERN = "(.*)";
   private final char[] regex;
   private final boolean caseSensitive;
   private final Pattern pattern;
   private int wildcardCount;
   private final List<String> variableNames = new ArrayList<>();

   RegexPathElement(int pos, char[] regex, boolean caseSensitive, char[] completePattern, char separator) {
      super(pos, separator);
      this.regex = regex;
      this.caseSensitive = caseSensitive;
      this.pattern = this.buildPattern(regex, completePattern);
   }

   public Pattern buildPattern(char[] regex, char[] completePattern) {
      StringBuilder patternBuilder = new StringBuilder();
      String text = new String(regex);
      Matcher matcher = GLOB_PATTERN.matcher(text);

      int end;
      for (end = 0; matcher.find(); end = matcher.end()) {
         patternBuilder.append(this.quote(text, end, matcher.start()));
         String match = matcher.group();
         if ("?".equals(match)) {
            patternBuilder.append('.');
         } else if ("*".equals(match)) {
            patternBuilder.append(".*");
            int pos = matcher.start();
            if (pos < 1 || text.charAt(pos - 1) != '.') {
               this.wildcardCount++;
            }
         } else if (match.startsWith("{") && match.endsWith("}")) {
            int colonIdx = match.indexOf(58);
            if (colonIdx == -1) {
               patternBuilder.append("(.*)");
               String variableName = matcher.group(1);
               if (this.variableNames.contains(variableName)) {
                  throw new PatternParseException(this.pos, completePattern, PatternParseException.PatternMessage.ILLEGAL_DOUBLE_CAPTURE, variableName);
               }

               this.variableNames.add(variableName);
            } else {
               String variablePattern = match.substring(colonIdx + 1, match.length() - 1);
               patternBuilder.append('(');
               patternBuilder.append(variablePattern);
               patternBuilder.append(')');
               String variableName = match.substring(1, colonIdx);
               if (this.variableNames.contains(variableName)) {
                  throw new PatternParseException(this.pos, completePattern, PatternParseException.PatternMessage.ILLEGAL_DOUBLE_CAPTURE, variableName);
               }

               this.variableNames.add(variableName);
            }
         }
      }

      patternBuilder.append(this.quote(text, end, text.length()));
      return Pattern.compile(patternBuilder.toString(), 32 | (this.caseSensitive ? 0 : 2));
   }

   public List<String> getVariableNames() {
      return this.variableNames;
   }

   private String quote(String s, int start, int end) {
      return start == end ? "" : Pattern.quote(s.substring(start, end));
   }

   @Override
   public boolean matches(int pathIndex, PathPattern.MatchingContext matchingContext) {
      String textToMatch = matchingContext.pathElementValue(pathIndex);
      Matcher matcher = this.pattern.matcher(textToMatch);
      boolean matches = matcher.matches();
      if (matches) {
         if (this.isNoMorePattern()) {
            if (!matchingContext.determineRemainingPath || !this.variableNames.isEmpty() && textToMatch.isEmpty()) {
               matches = pathIndex + 1 >= matchingContext.pathLength && (this.variableNames.isEmpty() || !textToMatch.isEmpty());
            } else {
               matchingContext.remainingPathIndex = pathIndex + 1;
               matches = true;
            }
         } else {
            matches = this.next != null && this.next.matches(pathIndex + 1, matchingContext);
         }
      }

      if (matches && matchingContext.extractingVariables) {
         if (this.variableNames.size() != matcher.groupCount()) {
            throw new IllegalArgumentException(
               "The number of capturing groups in the pattern segment "
                  + this.pattern
                  + " does not match the number of URI template variables it defines, which can occur if capturing groups are used in a URI template regex. Use non-capturing groups instead."
            );
         }

         for (int i = 1; i <= matcher.groupCount(); i++) {
            String name = this.variableNames.get(i - 1);
            String value = matcher.group(i);
            matchingContext.set(
               name,
               value,
               i == this.variableNames.size() ? ((PathContainer.PathSegment)matchingContext.pathElements.get(pathIndex)).parameters() : NO_PARAMETERS
            );
         }
      }

      return matches;
   }

   @Override
   public int getNormalizedLength() {
      int varsLength = 0;

      for (String variableName : this.variableNames) {
         varsLength += variableName.length();
      }

      return this.regex.length - varsLength - this.variableNames.size();
   }

   @Override
   public char[] getChars() {
      return this.regex;
   }

   @Override
   public int getCaptureCount() {
      return this.variableNames.size();
   }

   @Override
   public int getWildcardCount() {
      return this.wildcardCount;
   }

   @Override
   public int getScore() {
      return this.getCaptureCount() * 1 + this.getWildcardCount() * 100;
   }

   @Override
   public String toString() {
      return "Regex(" + this.regex + ")";
   }
}
