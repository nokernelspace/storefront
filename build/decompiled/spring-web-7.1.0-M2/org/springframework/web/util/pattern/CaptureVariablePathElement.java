package org.springframework.web.util.pattern;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.http.server.PathContainer;

class CaptureVariablePathElement extends PathElement {
   private final String variableName;
   private final @Nullable Pattern constraintPattern;

   CaptureVariablePathElement(int pos, char[] captureDescriptor, boolean caseSensitive, char separator) {
      super(pos, separator);
      int colon = -1;

      for (int i = 0; i < captureDescriptor.length; i++) {
         if (captureDescriptor[i] == ':') {
            colon = i;
            break;
         }
      }

      if (colon == -1) {
         this.variableName = new String(captureDescriptor, 1, captureDescriptor.length - 2);
         this.constraintPattern = null;
      } else {
         this.variableName = new String(captureDescriptor, 1, colon - 1);
         this.constraintPattern = Pattern.compile(new String(captureDescriptor, colon + 1, captureDescriptor.length - colon - 2), 32 | (caseSensitive ? 0 : 2));
      }
   }

   @Override
   public boolean matches(int pathIndex, PathPattern.MatchingContext matchingContext) {
      if (pathIndex >= matchingContext.pathLength) {
         return false;
      }

      String candidateCapture = matchingContext.pathElementValue(pathIndex);
      if (candidateCapture.isEmpty()) {
         return false;
      }

      if (this.constraintPattern != null) {
         Matcher matcher = this.constraintPattern.matcher(candidateCapture);
         if (matcher.groupCount() != 0) {
            throw new IllegalArgumentException("No capture groups allowed in the constraint regex: " + this.constraintPattern.pattern());
         }

         if (!matcher.matches()) {
            return false;
         }
      }

      boolean match = false;
      pathIndex++;
      if (this.isNoMorePattern()) {
         if (matchingContext.determineRemainingPath) {
            matchingContext.remainingPathIndex = pathIndex;
            match = true;
         } else {
            match = pathIndex == matchingContext.pathLength;
         }
      } else if (this.next != null) {
         match = this.next.matches(pathIndex, matchingContext);
      }

      if (match && matchingContext.extractingVariables) {
         matchingContext.set(this.variableName, candidateCapture, ((PathContainer.PathSegment)matchingContext.pathElements.get(pathIndex - 1)).parameters());
      }

      return match;
   }

   public String getVariableName() {
      return this.variableName;
   }

   @Override
   public int getNormalizedLength() {
      return 1;
   }

   @Override
   public char[] getChars() {
      StringBuilder sb = new StringBuilder();
      sb.append('{');
      sb.append(this.variableName);
      if (this.constraintPattern != null) {
         sb.append(':').append(this.constraintPattern.pattern());
      }

      sb.append('}');
      return sb.toString().toCharArray();
   }

   @Override
   public int getWildcardCount() {
      return 0;
   }

   @Override
   public int getCaptureCount() {
      return 1;
   }

   @Override
   public int getScore() {
      return 1;
   }

   @Override
   public String toString() {
      return "CaptureVariable({" + this.variableName + (this.constraintPattern != null ? ":" + this.constraintPattern.pattern() : "") + "})";
   }
}
