package org.springframework.http.server;

import java.util.List;
import org.springframework.util.MultiValueMap;

public interface PathContainer {
   String value();

   List<PathContainer.Element> elements();

   default PathContainer subPath(int index) {
      return this.subPath(index, this.elements().size());
   }

   default PathContainer subPath(int startIndex, int endIndex) {
      return DefaultPathContainer.subPath(this, startIndex, endIndex);
   }

   static PathContainer parsePath(String path) {
      return DefaultPathContainer.createFromUrlPath(path, PathContainer.Options.HTTP_PATH);
   }

   static PathContainer parsePath(String path, PathContainer.Options options) {
      return DefaultPathContainer.createFromUrlPath(path, options);
   }

   interface Element {
      String value();
   }

   class Options {
      public static final PathContainer.Options HTTP_PATH = create('/', true);
      public static final PathContainer.Options MESSAGE_ROUTE = create('.', false);
      private final char separator;
      private final boolean decodeAndParseSegments;

      private Options(char separator, boolean decodeAndParseSegments) {
         this.separator = separator;
         this.decodeAndParseSegments = decodeAndParseSegments;
      }

      public char separator() {
         return this.separator;
      }

      public boolean shouldDecodeAndParseSegments() {
         return this.decodeAndParseSegments;
      }

      public static PathContainer.Options create(char separator, boolean decodeAndParseSegments) {
         return new PathContainer.Options(separator, decodeAndParseSegments);
      }
   }

   interface PathSegment extends PathContainer.Element {
      String valueToMatch();

      char[] valueToMatchAsChars();

      MultiValueMap<String, String> parameters();
   }

   interface Separator extends PathContainer.Element {
   }
}
