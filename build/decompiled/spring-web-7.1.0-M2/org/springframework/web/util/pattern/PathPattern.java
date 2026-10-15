package org.springframework.web.util.pattern;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import org.jspecify.annotations.Nullable;
import org.springframework.http.server.PathContainer;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;

public class PathPattern implements Comparable<PathPattern> {
   private static final PathContainer EMPTY_PATH = PathContainer.parsePath("");
   public static final Comparator<@Nullable PathPattern> SPECIFICITY_COMPARATOR = Comparator.nullsLast(
      Comparator.<PathPattern>comparingInt(p -> p.isCatchAll() ? 1 : 0)
         .thenComparingInt(p -> p.isCatchAll() ? scoreByNormalizedLength(p) : 0)
         .thenComparingInt(PathPattern::getScore)
         .thenComparingInt(PathPattern::scoreByNormalizedLength)
   );
   private final String patternString;
   private final PathPatternParser parser;
   private final PathContainer.Options pathOptions;
   private final boolean caseSensitive;
   private final @Nullable PathElement head;
   private int capturedVariableCount;
   private int normalizedLength;
   private boolean endsWithSeparatorWildcard = false;
   private int score;
   private boolean catchAll = false;

   PathPattern(String patternText, PathPatternParser parser, @Nullable PathElement head) {
      this.patternString = patternText;
      this.parser = parser;
      this.pathOptions = parser.getPathOptions();
      this.caseSensitive = parser.isCaseSensitive();
      this.head = head;

      for (PathElement elem = head; elem != null; elem = elem.next) {
         this.capturedVariableCount = this.capturedVariableCount + elem.getCaptureCount();
         this.normalizedLength = this.normalizedLength + elem.getNormalizedLength();
         this.score = this.score + elem.getScore();
         if (elem instanceof CaptureSegmentsPathElement || elem instanceof WildcardSegmentsPathElement) {
            this.catchAll = true;
         }

         if (elem instanceof SeparatorPathElement && elem.next instanceof WildcardPathElement && elem.next.next == null) {
            this.endsWithSeparatorWildcard = true;
         }
      }
   }

   public String getPatternString() {
      return this.patternString;
   }

   public boolean hasPatternSyntax() {
      return this.score > 0 || this.catchAll || this.patternString.indexOf(63) != -1;
   }

   public boolean matches(PathContainer pathContainer) {
      if (this.head == null) {
         return !this.hasLength(pathContainer);
      }

      if (!this.hasLength(pathContainer)) {
         if (!(this.head instanceof WildcardSegmentsPathElement) && !(this.head instanceof CaptureSegmentsPathElement)) {
            return false;
         }

         pathContainer = EMPTY_PATH;
      }

      PathPattern.MatchingContext matchingContext = new PathPattern.MatchingContext(pathContainer, false);
      return this.head.matches(0, matchingContext);
   }

   public PathPattern.@Nullable PathMatchInfo matchAndExtract(PathContainer pathContainer) {
      if (this.head != null) {
         if (!this.hasLength(pathContainer)) {
            if (!(this.head instanceof WildcardSegmentsPathElement) && !(this.head instanceof CaptureSegmentsPathElement)) {
               return null;
            }

            pathContainer = EMPTY_PATH;
         }

         PathPattern.MatchingContext matchingContext = new PathPattern.MatchingContext(pathContainer, true);
         return this.head.matches(0, matchingContext) ? matchingContext.getPathMatchResult() : null;
      } else {
         return this.hasLength(pathContainer) && !this.pathContainerIsJustSeparator(pathContainer) ? null : PathPattern.PathMatchInfo.EMPTY;
      }
   }

   public PathPattern.@Nullable PathRemainingMatchInfo matchStartOfPath(PathContainer pathContainer) {
      if (this.head == null) {
         return new PathPattern.PathRemainingMatchInfo(EMPTY_PATH, pathContainer);
      }

      if (!this.hasLength(pathContainer)) {
         return null;
      }

      PathPattern.MatchingContext matchingContext = new PathPattern.MatchingContext(pathContainer, true);
      matchingContext.setMatchAllowExtraPath();
      boolean matches = this.head.matches(0, matchingContext);
      if (!matches) {
         return null;
      }

      PathContainer pathMatched;
      PathContainer pathRemaining;
      if (matchingContext.remainingPathIndex == pathContainer.elements().size()) {
         pathMatched = pathContainer;
         pathRemaining = EMPTY_PATH;
      } else {
         pathMatched = pathContainer.subPath(0, matchingContext.remainingPathIndex);
         pathRemaining = pathContainer.subPath(matchingContext.remainingPathIndex);
      }

      return new PathPattern.PathRemainingMatchInfo(pathMatched, pathRemaining, matchingContext.getPathMatchResult());
   }

   public PathContainer extractPathWithinPattern(PathContainer path) {
      List<PathContainer.Element> pathElements = path.elements();
      int pathElementsCount = pathElements.size();
      int startIndex = 0;

      PathElement elem;
      for (elem = this.head; elem != null && elem.isLiteral(); startIndex++) {
         elem = elem.next;
      }

      if (elem == null) {
         return PathContainer.parsePath("");
      }

      while (startIndex < pathElementsCount && pathElements.get(startIndex) instanceof PathContainer.Separator) {
         startIndex++;
      }

      int endIndex = pathElements.size();

      while (endIndex > 0 && pathElements.get(endIndex - 1) instanceof PathContainer.Separator) {
         endIndex--;
      }

      boolean multipleAdjacentSeparators = false;

      for (int i = startIndex; i < endIndex - 1; i++) {
         if (pathElements.get(i) instanceof PathContainer.Separator && pathElements.get(i + 1) instanceof PathContainer.Separator) {
            multipleAdjacentSeparators = true;
            break;
         }
      }

      PathContainer resultPath = null;
      if (multipleAdjacentSeparators) {
         StringBuilder sb = new StringBuilder();
         int i = startIndex;

         while (i < endIndex) {
            PathContainer.Element e = pathElements.get(i++);
            sb.append(e.value());
            if (e instanceof PathContainer.Separator) {
               while (i < endIndex && pathElements.get(i) instanceof PathContainer.Separator) {
                  i++;
               }
            }
         }

         resultPath = PathContainer.parsePath(sb.toString(), this.pathOptions);
      } else if (startIndex >= endIndex) {
         resultPath = PathContainer.parsePath("");
      } else {
         resultPath = path.subPath(startIndex, endIndex);
      }

      return resultPath;
   }

   public int compareTo(@Nullable PathPattern otherPattern) {
      int result = SPECIFICITY_COMPARATOR.compare(this, otherPattern);
      return result == 0 && otherPattern != null ? this.patternString.compareTo(otherPattern.patternString) : result;
   }

   public PathPattern combine(PathPattern otherPattern) {
      if (!StringUtils.hasLength(this.patternString)) {
         return !StringUtils.hasLength(otherPattern.patternString) ? this.parser.parse("") : otherPattern;
      }

      if (!StringUtils.hasLength(otherPattern.patternString)) {
         return this;
      }

      if (!this.patternString.equals(otherPattern.patternString)
         && this.capturedVariableCount == 0
         && this.matches(PathContainer.parsePath(otherPattern.patternString))) {
         return otherPattern;
      }

      if (this.endsWithSeparatorWildcard) {
         String prefix = this.patternString.length() > 2
            ? this.patternString.substring(0, this.patternString.length() - 2)
            : String.valueOf(this.getSeparator());
         return this.parser.parse(this.concat(prefix, otherPattern.patternString));
      }

      if (this.catchAll) {
         return this.parser.parse(this.concat(this.patternString.substring(0, this.patternString.length() - 3), otherPattern.patternString));
      }

      int firstStarDotPos = this.patternString.indexOf("*.");
      if (this.capturedVariableCount == 0 && firstStarDotPos != -1 && this.getSeparator() != '.') {
         int secondDotPos = otherPattern.patternString.indexOf(46);
         String firstExtension = this.patternString.substring(firstStarDotPos + 1);
         String secondExtension = secondDotPos == -1 ? "" : otherPattern.patternString.substring(secondDotPos);
         boolean isFirstExtensionWildcard = firstExtension.equals(".*") || firstExtension.isEmpty();
         boolean isSecondExtensionWildcard = secondExtension.equals(".*") || secondExtension.isEmpty();
         if (!isFirstExtensionWildcard && !isSecondExtensionWildcard) {
            throw new IllegalArgumentException("Cannot combine patterns: " + this.patternString + " and " + otherPattern);
         }

         String firstPath = this.patternString.substring(0, this.patternString.lastIndexOf(this.getSeparator()));
         String secondPath = otherPattern.patternString.substring(0, otherPattern.patternString.lastIndexOf(this.getSeparator()));
         if (!this.parser.parse(firstPath).matches(PathContainer.parsePath(secondPath))) {
            throw new IllegalArgumentException("Cannot combine patterns: " + this.patternString + " and " + otherPattern);
         }

         String secondFile = secondDotPos == -1 ? otherPattern.patternString : otherPattern.patternString.substring(0, secondDotPos);
         return this.parser.parse(secondFile + (isFirstExtensionWildcard ? secondExtension : firstExtension));
      } else {
         return this.parser.parse(this.concat(this.patternString, otherPattern.patternString));
      }
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof PathPattern that
            && this.patternString.equals(that.getPatternString())
            && this.getSeparator() == that.getSeparator()
            && this.caseSensitive == that.caseSensitive;
   }

   @Override
   public int hashCode() {
      return (this.patternString.hashCode() + this.getSeparator()) * 17 + (this.caseSensitive ? 1 : 0);
   }

   @Override
   public String toString() {
      return this.patternString;
   }

   int getScore() {
      return this.score;
   }

   boolean isCatchAll() {
      return this.catchAll;
   }

   int getNormalizedLength() {
      return this.normalizedLength;
   }

   char getSeparator() {
      return this.pathOptions.separator();
   }

   int getCapturedVariableCount() {
      return this.capturedVariableCount;
   }

   String toChainString() {
      StringJoiner stringJoiner = new StringJoiner(" ");

      for (PathElement pe = this.head; pe != null; pe = pe.next) {
         stringJoiner.add(pe.toString());
      }

      return stringJoiner.toString();
   }

   String computePatternString() {
      StringBuilder sb = new StringBuilder();

      for (PathElement pe = this.head; pe != null; pe = pe.next) {
         sb.append(pe.getChars());
      }

      return sb.toString();
   }

   @Nullable PathElement getHeadSection() {
      return this.head;
   }

   private String concat(String path1, String path2) {
      boolean path1EndsWithSeparator = path1.charAt(path1.length() - 1) == this.getSeparator();
      boolean path2StartsWithSeparator = path2.charAt(0) == this.getSeparator();
      if (path1EndsWithSeparator && path2StartsWithSeparator) {
         return path1 + path2.substring(1);
      } else {
         return !path1EndsWithSeparator && !path2StartsWithSeparator ? path1 + this.getSeparator() + path2 : path1 + path2;
      }
   }

   private boolean hasLength(@Nullable PathContainer container) {
      return container != null && !container.elements().isEmpty();
   }

   private static int scoreByNormalizedLength(PathPattern pattern) {
      return -pattern.getNormalizedLength();
   }

   private boolean pathContainerIsJustSeparator(PathContainer pathContainer) {
      return pathContainer.value().length() == 1 && pathContainer.value().charAt(0) == this.getSeparator();
   }

   static class MatchingContext {
      final PathContainer candidate;
      final List<PathContainer.Element> pathElements;
      final int pathLength;
      private @Nullable Map<String, String> extractedUriVariables;
      private @Nullable Map<String, MultiValueMap<String, String>> extractedMatrixVariables;
      boolean extractingVariables;
      boolean determineRemainingPath = false;
      int remainingPathIndex;

      public MatchingContext(PathContainer pathContainer, boolean extractVariables) {
         this.candidate = pathContainer;
         this.pathElements = pathContainer.elements();
         this.pathLength = this.pathElements.size();
         this.extractingVariables = extractVariables;
      }

      public void setMatchAllowExtraPath() {
         this.determineRemainingPath = true;
      }

      public void set(String key, String value, MultiValueMap<String, String> parameters) {
         if (this.extractedUriVariables == null) {
            this.extractedUriVariables = new HashMap<>();
         }

         this.extractedUriVariables.put(key, value);
         if (!parameters.isEmpty()) {
            if (this.extractedMatrixVariables == null) {
               this.extractedMatrixVariables = new HashMap<>();
            }

            this.extractedMatrixVariables.put(key, CollectionUtils.unmodifiableMultiValueMap(parameters));
         }
      }

      public PathPattern.PathMatchInfo getPathMatchResult() {
         return this.extractedUriVariables == null
            ? PathPattern.PathMatchInfo.EMPTY
            : new PathPattern.PathMatchInfo(this.extractedUriVariables, this.extractedMatrixVariables);
      }

      boolean isSeparator(int pathIndex) {
         return this.pathElements.get(pathIndex) instanceof PathContainer.Separator;
      }

      String pathElementValue(int pathIndex) {
         return (pathIndex < this.pathLength ? (PathContainer.Element)this.pathElements.get(pathIndex) : null) instanceof PathContainer.PathSegment pathSegment
            ? pathSegment.valueToMatch()
            : "";
      }
   }

   public static class PathMatchInfo {
      private static final PathPattern.PathMatchInfo EMPTY = new PathPattern.PathMatchInfo(Collections.emptyMap(), Collections.emptyMap());
      private final Map<String, String> uriVariables;
      private final Map<String, MultiValueMap<String, String>> matrixVariables;

      PathMatchInfo(Map<String, String> uriVars, @Nullable Map<String, MultiValueMap<String, String>> matrixVars) {
         this.uriVariables = Collections.unmodifiableMap(uriVars);
         this.matrixVariables = matrixVars != null ? Collections.unmodifiableMap(matrixVars) : Collections.emptyMap();
      }

      public Map<String, String> getUriVariables() {
         return this.uriVariables;
      }

      public Map<String, MultiValueMap<String, String>> getMatrixVariables() {
         return this.matrixVariables;
      }

      @Override
      public String toString() {
         return "PathMatchInfo[uriVariables=" + this.uriVariables + ", matrixVariables=" + this.matrixVariables + "]";
      }
   }

   public static class PathRemainingMatchInfo {
      private final PathContainer pathMatched;
      private final PathContainer pathRemaining;
      private final PathPattern.PathMatchInfo pathMatchInfo;

      PathRemainingMatchInfo(PathContainer pathMatched, PathContainer pathRemaining) {
         this(pathMatched, pathRemaining, PathPattern.PathMatchInfo.EMPTY);
      }

      PathRemainingMatchInfo(PathContainer pathMatched, PathContainer pathRemaining, PathPattern.PathMatchInfo pathMatchInfo) {
         this.pathRemaining = pathRemaining;
         this.pathMatched = pathMatched;
         this.pathMatchInfo = pathMatchInfo;
      }

      public PathContainer getPathMatched() {
         return this.pathMatched;
      }

      public PathContainer getPathRemaining() {
         return this.pathRemaining;
      }

      public Map<String, String> getUriVariables() {
         return this.pathMatchInfo.getUriVariables();
      }

      public Map<String, MultiValueMap<String, String>> getMatrixVariables() {
         return this.pathMatchInfo.getMatrixVariables();
      }
   }
}
