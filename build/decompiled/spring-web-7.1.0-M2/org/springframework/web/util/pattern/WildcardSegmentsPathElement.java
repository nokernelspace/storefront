package org.springframework.web.util.pattern;

class WildcardSegmentsPathElement extends PathElement {
   WildcardSegmentsPathElement(int pos, char separator) {
      super(pos, separator);
   }

   @Override
   public boolean matches(int pathIndex, PathPattern.MatchingContext matchingContext) {
      if (pathIndex == 0 && this.next != null) {
         for (int endPathIndex = pathIndex; endPathIndex < matchingContext.pathLength; endPathIndex++) {
            if (this.next.matches(endPathIndex, matchingContext)) {
               return true;
            }
         }

         return false;
      } else {
         if (pathIndex < matchingContext.pathLength && !matchingContext.isSeparator(pathIndex)) {
            return false;
         }

         if (matchingContext.determineRemainingPath) {
            matchingContext.remainingPathIndex = matchingContext.pathLength;
         }

         return true;
      }
   }

   @Override
   public int getNormalizedLength() {
      return 1;
   }

   @Override
   public char[] getChars() {
      return (this.separator + "**").toCharArray();
   }

   @Override
   public int getWildcardCount() {
      return 1;
   }

   @Override
   public String toString() {
      return "WildcardSegments(" + this.separator + "**)";
   }
}
