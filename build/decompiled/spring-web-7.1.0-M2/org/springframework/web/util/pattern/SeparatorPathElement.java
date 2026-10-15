package org.springframework.web.util.pattern;

class SeparatorPathElement extends PathElement {
   SeparatorPathElement(int pos, char separator) {
      super(pos, separator);
   }

   @Override
   public boolean matches(int pathIndex, PathPattern.MatchingContext matchingContext) {
      if (pathIndex >= matchingContext.pathLength || !matchingContext.isSeparator(pathIndex)) {
         return false;
      } else if (!this.isNoMorePattern()) {
         pathIndex++;
         return this.next != null && this.next.matches(pathIndex, matchingContext);
      } else if (matchingContext.determineRemainingPath) {
         matchingContext.remainingPathIndex = pathIndex + 1;
         return true;
      } else {
         return pathIndex + 1 == matchingContext.pathLength;
      }
   }

   @Override
   public int getNormalizedLength() {
      return 1;
   }

   @Override
   public char[] getChars() {
      return new char[]{this.separator};
   }

   @Override
   public boolean isLiteral() {
      return true;
   }

   @Override
   public String toString() {
      return "Separator(" + this.separator + ")";
   }
}
