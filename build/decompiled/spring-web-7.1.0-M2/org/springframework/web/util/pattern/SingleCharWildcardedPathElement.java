package org.springframework.web.util.pattern;

import org.springframework.http.server.PathContainer;

class SingleCharWildcardedPathElement extends PathElement {
   private final char[] text;
   private final int len;
   private final int questionMarkCount;
   private final boolean caseSensitive;

   public SingleCharWildcardedPathElement(int pos, char[] literalText, int questionMarkCount, boolean caseSensitive, char separator) {
      super(pos, separator);
      this.len = literalText.length;
      this.questionMarkCount = questionMarkCount;
      this.caseSensitive = caseSensitive;
      if (caseSensitive) {
         this.text = literalText;
      } else {
         this.text = new char[literalText.length];

         for (int i = 0; i < this.len; i++) {
            this.text[i] = Character.toLowerCase(literalText[i]);
         }
      }
   }

   @Override
   public boolean matches(int pathIndex, PathPattern.MatchingContext matchingContext) {
      if (pathIndex >= matchingContext.pathLength) {
         return false;
      }

      PathContainer.Element element = matchingContext.pathElements.get(pathIndex);
      if (element instanceof PathContainer.PathSegment pathSegment) {
         String value = pathSegment.valueToMatch();
         if (value.length() != this.len) {
            return false;
         }

         if (this.caseSensitive) {
            for (int i = 0; i < this.len; i++) {
               char ch = this.text[i];
               if (ch != '?' && ch != value.charAt(i)) {
                  return false;
               }
            }
         } else {
            for (int i = 0; i < this.len; i++) {
               char ch = this.text[i];
               char valCh = value.charAt(i);
               if (ch != valCh && ch != '?' && ch != Character.toLowerCase(valCh)) {
                  return false;
               }
            }
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
      } else {
         return false;
      }
   }

   @Override
   public int getWildcardCount() {
      return this.questionMarkCount;
   }

   @Override
   public int getNormalizedLength() {
      return this.len;
   }

   @Override
   public char[] getChars() {
      return this.text;
   }

   @Override
   public String toString() {
      return "SingleCharWildcarded(" + this.text + ")";
   }
}
