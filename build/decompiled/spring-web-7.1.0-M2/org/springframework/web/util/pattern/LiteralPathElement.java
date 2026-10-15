package org.springframework.web.util.pattern;

import org.springframework.http.server.PathContainer;

class LiteralPathElement extends PathElement {
   private final String text;
   private final int len;
   private final boolean caseSensitive;

   public LiteralPathElement(int pos, char[] literalText, boolean caseSensitive, char separator) {
      super(pos, separator);
      this.len = literalText.length;
      this.caseSensitive = caseSensitive;
      this.text = new String(literalText);
   }

   @Override
   public boolean matches(int pathIndex, PathPattern.MatchingContext matchingContext) {
      if (pathIndex >= matchingContext.pathLength) {
         return false;
      } else {
         PathContainer.Element element = matchingContext.pathElements.get(pathIndex);
         if (!(element instanceof PathContainer.PathSegment pathSegment)) {
            return false;
         } else {
            String value = pathSegment.valueToMatch();
            if (value.length() != this.len) {
               return false;
            }

            if (this.caseSensitive) {
               if (!this.text.equals(value)) {
                  return false;
               }
            } else if (!this.text.equalsIgnoreCase(value)) {
               return false;
            }

            pathIndex++;
            if (this.isNoMorePattern()) {
               if (matchingContext.determineRemainingPath) {
                  matchingContext.remainingPathIndex = pathIndex;
                  return true;
               } else {
                  return pathIndex == matchingContext.pathLength;
               }
            } else {
               return this.next != null && this.next.matches(pathIndex, matchingContext);
            }
         }
      }
   }

   @Override
   public int getNormalizedLength() {
      return this.len;
   }

   @Override
   public char[] getChars() {
      return this.text.toCharArray();
   }

   @Override
   public boolean isLiteral() {
      return true;
   }

   @Override
   public String toString() {
      return "Literal(" + this.text + ")";
   }
}
