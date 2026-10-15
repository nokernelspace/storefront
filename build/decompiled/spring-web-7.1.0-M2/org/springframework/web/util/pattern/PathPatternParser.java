package org.springframework.web.util.pattern;

import org.springframework.http.server.PathContainer;
import org.springframework.util.StringUtils;

public class PathPatternParser {
   private boolean caseSensitive = true;
   private PathContainer.Options pathOptions = PathContainer.Options.HTTP_PATH;
   public static final PathPatternParser defaultInstance = new PathPatternParser() {
      @Override
      public void setCaseSensitive(boolean caseSensitive) {
         this.raiseError();
      }

      @Override
      public void setPathOptions(PathContainer.Options pathOptions) {
         this.raiseError();
      }

      private void raiseError() {
         throw new UnsupportedOperationException("This is a read-only, shared instance that cannot be modified");
      }
   };

   public void setCaseSensitive(boolean caseSensitive) {
      this.caseSensitive = caseSensitive;
   }

   public boolean isCaseSensitive() {
      return this.caseSensitive;
   }

   public void setPathOptions(PathContainer.Options pathOptions) {
      this.pathOptions = pathOptions;
   }

   public PathContainer.Options getPathOptions() {
      return this.pathOptions;
   }

   public String initFullPathPattern(String pattern) {
      return StringUtils.hasLength(pattern) && !pattern.startsWith("/") ? "/" + pattern : pattern;
   }

   public PathPattern parse(String pathPattern) throws PatternParseException {
      return new InternalPathPatternParser(this).parse(pathPattern);
   }
}
