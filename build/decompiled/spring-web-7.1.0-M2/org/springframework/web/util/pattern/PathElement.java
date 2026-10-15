package org.springframework.web.util.pattern;

import org.jspecify.annotations.Nullable;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

abstract class PathElement {
   protected static final int WILDCARD_WEIGHT = 100;
   protected static final int CAPTURE_VARIABLE_WEIGHT = 1;
   protected static final MultiValueMap<String, String> NO_PARAMETERS = new LinkedMultiValueMap();
   protected final int pos;
   protected final char separator;
   protected @Nullable PathElement next;
   protected @Nullable PathElement prev;

   PathElement(int pos, char separator) {
      this.pos = pos;
      this.separator = separator;
   }

   public abstract boolean matches(int candidatePos, PathPattern.MatchingContext matchingContext);

   public abstract int getNormalizedLength();

   public abstract char[] getChars();

   public int getCaptureCount() {
      return 0;
   }

   public int getWildcardCount() {
      return 0;
   }

   public int getScore() {
      return 0;
   }

   public boolean isLiteral() {
      return false;
   }

   protected final boolean isNoMorePattern() {
      return this.next == null;
   }
}
