package org.springframework.web.util.pattern;

import org.springframework.http.server.PathContainer;

class WildcardPathElement extends PathElement {
   public WildcardPathElement(int pos, char separator) {
      super(pos, separator);
   }

   @Override
   public boolean matches(int pathIndex, PathPattern.MatchingContext matchingContext) {
      String segmentData = null;
      if (pathIndex < matchingContext.pathLength) {
         PathContainer.Element element = matchingContext.pathElements.get(pathIndex);
         if (!(element instanceof PathContainer.PathSegment pathSegment)) {
            return false;
         }

         segmentData = pathSegment.valueToMatch();
         pathIndex++;
      }

      if (this.isNoMorePattern()) {
         if (matchingContext.determineRemainingPath) {
            matchingContext.remainingPathIndex = pathIndex;
            return true;
         } else {
            return pathIndex == matchingContext.pathLength;
         }
      } else {
         return segmentData != null && !segmentData.isEmpty() ? this.next != null && this.next.matches(pathIndex, matchingContext) : false;
      }
   }

   @Override
   public int getNormalizedLength() {
      return 1;
   }

   @Override
   public char[] getChars() {
      return new char[]{'*'};
   }

   @Override
   public int getWildcardCount() {
      return 1;
   }

   @Override
   public int getScore() {
      return 100;
   }

   @Override
   public String toString() {
      return "Wildcard(*)";
   }
}
