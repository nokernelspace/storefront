package org.springframework.beans;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public final class PropertyPath {
   private final String canonicalName;
   private final List<PropertyPath.Segment> segments;

   private PropertyPath(String canonicalName, List<PropertyPath.Segment> segments) {
      this.canonicalName = canonicalName;
      this.segments = segments;
   }

   public static PropertyPath parse(String path) throws InvalidPropertyPathException {
      return parse(path, PropertyPath.Options.UNLIMITED);
   }

   public static PropertyPath parse(String path, PropertyPath.Options options) throws InvalidPropertyPathException {
      Assert.notNull(path, "Property path must not be null");
      Assert.notNull(options, "Options must not be null");
      if (path.isEmpty()) {
         return new PropertyPath("", Collections.emptyList());
      } else {
         PropertyPath parsed = new PropertyPath.Parser(path).parse();
         int nestingDepth = parsed.segments.size() - 1;
         if (nestingDepth > options.maxNestedPathDepth) {
            throw new InvalidPropertyPathException(path, "nesting depth exceeds the maximum of " + options.maxNestedPathDepth);
         } else {
            return parsed;
         }
      }
   }

   public static String canonicalNameOrOriginal(@Nullable String path) {
      if (path == null) {
         return "";
      }

      try {
         return parse(path).canonicalName();
      } catch (InvalidPropertyPathException ex) {
         return path;
      }
   }

   public String canonicalName() {
      return this.canonicalName;
   }

   public List<PropertyPath.Segment> segments() {
      return this.segments;
   }

   public PropertyPath subPath(int fromIndex) {
      if (fromIndex == 0) {
         return this;
      }

      List<PropertyPath.Segment> subSegments = this.segments.subList(fromIndex, this.segments.size());
      StringBuilder subCanonicalName = new StringBuilder();

      for (int i = 0; i < subSegments.size(); i++) {
         if (i > 0) {
            subCanonicalName.append('.');
         }

         subCanonicalName.append(subSegments.get(i).toCanonicalName());
      }

      return new PropertyPath(subCanonicalName.toString(), subSegments);
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof PropertyPath that && this.canonicalName.equals(that.canonicalName);
   }

   @Override
   public int hashCode() {
      return this.canonicalName.hashCode();
   }

   @Override
   public String toString() {
      return this.canonicalName;
   }

   public static final class Options {
      public static final PropertyPath.Options UNLIMITED = new PropertyPath.Options(Integer.MAX_VALUE);
      private final int maxNestedPathDepth;

      private Options(int maxNestedPathDepth) {
         this.maxNestedPathDepth = maxNestedPathDepth;
      }

      public static PropertyPath.Options withMaxNestedPathDepth(int maxNestedPathDepth) {
         Assert.isTrue(maxNestedPathDepth >= 0, "'maxNestedPathDepth' must not be negative");
         return new PropertyPath.Options(maxNestedPathDepth);
      }
   }

   private static final class Parser {
      private final String path;
      private final List<PropertyPath.Segment> segments = new ArrayList<>(2);
      private final StringBuilder canonicalName;
      private int pos;
      private int segmentStart;
      private int segmentNameEnd = -1;
      private @Nullable List<String> keys;
      private int keyStart;
      private int depth;
      private char quoteChar;

      Parser(String path) {
         this.path = path;
         this.canonicalName = new StringBuilder(path.length());
      }

      PropertyPath parse() {
         PropertyPath.State state = PropertyPath.State.NAME;

         while (this.pos < this.path.length()) {
            state = state.process(this.path.charAt(this.pos), this);
            this.pos++;
         }

         state.onEof(this);
         return new PropertyPath(this.canonicalName.toString(), Collections.unmodifiableList(this.segments));
      }

      private void addKey(String key) {
         List<String> keys = this.keys;
         if (keys == null) {
            keys = new ArrayList<>(2);
            this.keys = keys;
         }

         keys.add(key);
      }

      private void endSegment() {
         String name = this.path.substring(this.segmentStart, this.segmentNameEnd);
         List<String> keys = this.keys != null ? this.keys : Collections.emptyList();
         if (name.isEmpty() && keys.isEmpty()) {
            throw new InvalidPropertyPathException(this.path, "empty path segment (at position " + this.segmentStart + ")");
         }

         if (!this.segments.isEmpty()) {
            this.canonicalName.append('.');
         }

         PropertyPath.Segment segment = new PropertyPath.Segment(name, keys);
         this.segments.add(segment);
         this.canonicalName.append(segment.toCanonicalName());
         this.keys = null;
         this.segmentNameEnd = -1;
      }

      private InvalidPropertyPathException error(String reason) {
         return new InvalidPropertyPathException(this.path, reason + " (at position " + this.pos + ")");
      }

      private InvalidPropertyPathException unclosedIndex() {
         return this.error("unclosed '['");
      }
   }

   public record Segment(String name, List<String> keys) {
      public Segment(String name, List<String> keys) {
         Assert.notNull(name, "Segment name must not be null");
         Assert.notNull(keys, "Segment keys must not be null");
         this.name = name;
         this.keys = List.copyOf(keys);
      }

      public String toCanonicalName() {
         StringBuilder canonicalNameBuilder = new StringBuilder();
         canonicalNameBuilder.append(this.name);

         for (String key : this.keys) {
            this.appendCanonicalKey(canonicalNameBuilder, key);
         }

         return canonicalNameBuilder.toString();
      }

      public PropertyPath.Segment withoutLastKey() {
         return this.keys.isEmpty() ? this : new PropertyPath.Segment(this.name, this.keys.subList(0, this.keys.size() - 1));
      }

      private void appendCanonicalKey(StringBuilder canonicalNameBuilder, String key) {
         char quoteChar = canonicalQuoteChar(key);
         canonicalNameBuilder.append('[');
         if (quoteChar != 0) {
            canonicalNameBuilder.append(quoteChar);
         }

         canonicalNameBuilder.append(key);
         if (quoteChar != 0) {
            canonicalNameBuilder.append(quoteChar);
         }

         canonicalNameBuilder.append(']');
      }

      private static char canonicalQuoteChar(String key) {
         int depth = 0;
         boolean balanced = true;
         boolean containsSingleQuote = false;
         boolean containsDoubleQuote = false;

         for (int i = 0; i < key.length(); i++) {
            switch (key.charAt(i)) {
               case '"':
                  containsDoubleQuote = true;
                  break;
               case '\'':
                  containsSingleQuote = true;
                  break;
               case '[':
                  depth++;
                  break;
               case ']':
                  if (--depth < 0) {
                     balanced = false;
                  }
            }
         }

         if (balanced && depth == 0 && !containsSingleQuote && !containsDoubleQuote) {
            return '\u0000';
         } else {
            return (char)(containsSingleQuote && !containsDoubleQuote ? '"' : '\'');
         }
      }
   }

   private enum State {
      NAME {
         @Override
         PropertyPath.State process(char ch, PropertyPath.Parser parser) {
            if (ch == '.') {
               parser.segmentNameEnd = parser.pos;
               parser.endSegment();
               parser.segmentStart = parser.pos + 1;
               return this;
            } else if (ch == '[') {
               parser.segmentNameEnd = parser.pos;
               return INDEX_OPEN;
            } else if (ch == ']') {
               throw parser.error("unexpected ']' without a matching '['");
            } else {
               return this;
            }
         }

         @Override
         void onEof(PropertyPath.Parser parser) {
            parser.segmentNameEnd = parser.path.length();
            parser.endSegment();
         }
      },
      INDEX_OPEN {
         @Override
         PropertyPath.State process(char ch, PropertyPath.Parser parser) {
            if (ch == ']') {
               parser.addKey("");
               return AFTER_INDEX;
            } else if (ch != '\'' && ch != '"') {
               parser.keyStart = parser.pos;
               parser.depth = ch == '[' ? 1 : 0;
               return RAW_KEY;
            } else {
               parser.quoteChar = ch;
               parser.keyStart = parser.pos + 1;
               return QUOTED_KEY;
            }
         }

         @Override
         void onEof(PropertyPath.Parser parser) {
            throw parser.unclosedIndex();
         }
      },
      RAW_KEY {
         @Override
         PropertyPath.State process(char ch, PropertyPath.Parser parser) {
            if (ch == '[') {
               parser.depth++;
               return this;
            }

            if (ch == ']') {
               if (parser.depth == 0) {
                  parser.addKey(parser.path.substring(parser.keyStart, parser.pos));
                  return AFTER_INDEX;
               } else {
                  parser.depth--;
                  return this;
               }
            } else if (ch != '\'' && ch != '"') {
               return this;
            } else {
               throw parser.error("unexpected quote '" + ch + "' in an unquoted key; quote the whole key to use quote characters within it");
            }
         }

         @Override
         void onEof(PropertyPath.Parser parser) {
            throw parser.unclosedIndex();
         }
      },
      QUOTED_KEY {
         @Override
         PropertyPath.State process(char ch, PropertyPath.Parser parser) {
            if (ch == parser.quoteChar) {
               parser.addKey(parser.path.substring(parser.keyStart, parser.pos));
               return QUOTE_CLOSED;
            } else {
               return this;
            }
         }

         @Override
         void onEof(PropertyPath.Parser parser) {
            throw parser.error("unterminated quote '" + parser.quoteChar + "'");
         }
      },
      QUOTE_CLOSED {
         @Override
         PropertyPath.State process(char ch, PropertyPath.Parser parser) {
            if (ch != ']') {
               throw parser.error("unexpected '" + ch + "' after a closing quote; expected ']'");
            } else {
               return AFTER_INDEX;
            }
         }

         @Override
         void onEof(PropertyPath.Parser parser) {
            throw parser.unclosedIndex();
         }
      },
      AFTER_INDEX {
         @Override
         PropertyPath.State process(char ch, PropertyPath.Parser parser) {
            if (ch == '.') {
               parser.endSegment();
               parser.segmentStart = parser.pos + 1;
               return NAME;
            } else if (ch == '[') {
               return INDEX_OPEN;
            } else {
               throw parser.error("unexpected '" + ch + "' after an index; expected '.', '[', or the end of the path");
            }
         }

         @Override
         void onEof(PropertyPath.Parser parser) {
            parser.endSegment();
         }
      };

      abstract PropertyPath.State process(char ch, PropertyPath.Parser parser);

      abstract void onEof(PropertyPath.Parser parser);
   }
}
