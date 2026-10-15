package org.springframework.util;

import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.BiPredicate;
import org.jspecify.annotations.Nullable;

public abstract class MimeTypeUtils {
   private static final byte[] BOUNDARY_CHARS = new byte[]{
      45,
      95,
      49,
      50,
      51,
      52,
      53,
      54,
      55,
      56,
      57,
      48,
      97,
      98,
      99,
      100,
      101,
      102,
      103,
      104,
      105,
      106,
      107,
      108,
      109,
      110,
      111,
      112,
      113,
      114,
      115,
      116,
      117,
      118,
      119,
      120,
      121,
      122,
      65,
      66,
      67,
      68,
      69,
      70,
      71,
      72,
      73,
      74,
      75,
      76,
      77,
      78,
      79,
      80,
      81,
      82,
      83,
      84,
      85,
      86,
      87,
      88,
      89,
      90
   };
   public static final MimeType ALL = new MimeType("*", "*");
   public static final String ALL_VALUE = "*/*";
   public static final MimeType APPLICATION_GRAPHQL = new MimeType("application", "graphql+json");
   public static final String APPLICATION_GRAPHQL_VALUE = "application/graphql+json";
   public static final MimeType APPLICATION_JSON = new MimeType("application", "json");
   public static final String APPLICATION_JSON_VALUE = "application/json";
   public static final MimeType APPLICATION_OCTET_STREAM = new MimeType("application", "octet-stream");
   public static final String APPLICATION_OCTET_STREAM_VALUE = "application/octet-stream";
   public static final MimeType APPLICATION_XML = new MimeType("application", "xml");
   public static final String APPLICATION_XML_VALUE = "application/xml";
   public static final MimeType IMAGE_GIF = new MimeType("image", "gif");
   public static final String IMAGE_GIF_VALUE = "image/gif";
   public static final MimeType IMAGE_JPEG = new MimeType("image", "jpeg");
   public static final String IMAGE_JPEG_VALUE = "image/jpeg";
   public static final MimeType IMAGE_PNG = new MimeType("image", "png");
   public static final String IMAGE_PNG_VALUE = "image/png";
   public static final MimeType TEXT_HTML = new MimeType("text", "html");
   public static final String TEXT_HTML_VALUE = "text/html";
   public static final MimeType TEXT_PLAIN = new MimeType("text", "plain");
   public static final String TEXT_PLAIN_VALUE = "text/plain";
   public static final MimeType TEXT_XML = new MimeType("text", "xml");
   public static final String TEXT_XML_VALUE = "text/xml";
   private static final ConcurrentLruCache<String, MimeType> cachedMimeTypes = new ConcurrentLruCache<>(64, MimeTypeUtils::parseMimeTypeInternal);
   private static volatile @Nullable Random random;

   public static MimeType parseMimeType(String mimeType) {
      if (!StringUtils.hasLength(mimeType)) {
         throw new InvalidMimeTypeException(mimeType, "'mimeType' must not be empty");
      } else {
         return mimeType.startsWith("multipart") ? parseMimeTypeInternal(mimeType) : cachedMimeTypes.get(mimeType);
      }
   }

   private static MimeType parseMimeTypeInternal(String mimeType) {
      return new MimeTypeUtils.MimeTypeParser(mimeType).parse();
   }

   public static List<MimeType> parseMimeTypes(String mimeTypes) {
      if (!StringUtils.hasLength(mimeTypes)) {
         return Collections.emptyList();
      }

      List<String> tokens = tokenize(mimeTypes);
      List<MimeType> result = new ArrayList<>(tokens.size());

      for (String token : tokens) {
         result.add(parseMimeType(token));
      }

      return result;
   }

   @Deprecated(since = "7.1.0", forRemoval = true)
   public static List<String> tokenize(String mimeTypes) {
      if (!StringUtils.hasLength(mimeTypes)) {
         return Collections.emptyList();
      }

      if (mimeTypes.indexOf(34) == -1) {
         return splitByComma(mimeTypes);
      }

      List<String> tokens = new ArrayList<>();
      boolean inQuotes = false;
      int startIndex = 0;

      for (int i = 0; i < mimeTypes.length(); i++) {
         switch (mimeTypes.charAt(i)) {
            case '"':
               inQuotes = !inQuotes;
               break;
            case ',':
               if (!inQuotes) {
                  String trimmed = mimeTypes.substring(startIndex, i).trim();
                  if (!trimmed.isEmpty()) {
                     tokens.add(trimmed);
                  }

                  startIndex = i + 1;
               }
               break;
            case '\\':
               i++;
         }
      }

      String trimmed = mimeTypes.substring(startIndex).trim();
      if (!trimmed.isEmpty()) {
         tokens.add(trimmed);
      }

      return tokens;
   }

   private static List<String> splitByComma(String mimeTypes) {
      List<String> tokens = new ArrayList<>();
      int startIndex = 0;

      int commaIndex;
      while ((commaIndex = mimeTypes.indexOf(44, startIndex)) != -1) {
         String trimmed = mimeTypes.substring(startIndex, commaIndex).trim();
         if (!trimmed.isEmpty()) {
            tokens.add(trimmed);
         }

         startIndex = commaIndex + 1;
      }

      String trimmed = mimeTypes.substring(startIndex).trim();
      if (!trimmed.isEmpty()) {
         tokens.add(trimmed);
      }

      return tokens;
   }

   public static String toString(Collection<? extends MimeType> mimeTypes) {
      StringBuilder builder = new StringBuilder();
      Iterator<? extends MimeType> iterator = mimeTypes.iterator();

      while (iterator.hasNext()) {
         MimeType mimeType = iterator.next();
         mimeType.appendTo(builder);
         if (iterator.hasNext()) {
            builder.append(", ");
         }
      }

      return builder.toString();
   }

   public static <T extends MimeType> void sortBySpecificity(List<T> mimeTypes) {
      Assert.notNull(mimeTypes, "'mimeTypes' must not be null");
      if (mimeTypes.size() > 50) {
         throw new InvalidMimeTypeException(mimeTypes.toString(), "Too many elements");
      }

      bubbleSort(mimeTypes, MimeType::isLessSpecific);
   }

   static <T> void bubbleSort(List<T> list, BiPredicate<? super T, ? super T> swap) {
      int len = list.size();

      for (int i = 0; i < len; i++) {
         for (int j = 1; j < len - i; j++) {
            T prev = list.get(j - 1);
            T cur = list.get(j);
            if (swap.test(prev, cur)) {
               list.set(j, prev);
               list.set(j - 1, cur);
            }
         }
      }
   }

   public static byte[] generateMultipartBoundary() {
      Random randomToUse = initRandom();
      byte[] boundary = new byte[randomToUse.nextInt(11) + 30];

      for (int i = 0; i < boundary.length; i++) {
         boundary[i] = BOUNDARY_CHARS[randomToUse.nextInt(BOUNDARY_CHARS.length)];
      }

      return boundary;
   }

   private static Random initRandom() {
      Random randomToUse = random;
      if (randomToUse == null) {
         synchronized (MimeTypeUtils.class) {
            randomToUse = random;
            if (randomToUse == null) {
               randomToUse = new SecureRandom();
               random = randomToUse;
            }
         }
      }

      return randomToUse;
   }

   public static String generateMultipartBoundaryString() {
      return new String(generateMultipartBoundary(), StandardCharsets.US_ASCII);
   }

   private static final class MimeTypeParser {
      private final String input;
      private int index = 0;
      private int mark = 0;
      private String type = "";
      private String subtype = "";
      private @Nullable Map<String, String> parameters;
      private @Nullable String paramName;
      private @Nullable MimeType parsed;

      private MimeTypeParser(String input) {
         this.input = input;
      }

      private MimeType parse() {
         MimeTypeUtils.ParserState state = MimeTypeUtils.ParserState.INITIAL;

         while (this.index < this.input.length()) {
            char c = this.input.charAt(this.index);
            state = state.process(c, this);
            this.index++;
         }

         state.onEof(this);
         if (this.parsed == null) {
            throw new InvalidMimeTypeException(this.input, "'mimeType' must not be empty");
         } else {
            return this.parsed;
         }
      }

      private void resolveBareType(String candidate) {
         if (!"*".equals(candidate)) {
            throw new InvalidMimeTypeException(this.input, "does not contain '/'");
         }

         this.type = "*";
         this.subtype = "*";
      }

      private void putParameter(String name, String value) {
         if (this.parameters == null) {
            this.parameters = new LinkedCaseInsensitiveMap<>(4, Locale.ROOT);
         }

         if (this.parameters.put(name, value) != null) {
            throw new InvalidMimeTypeException(this.input, "duplicate parameter '" + name + "=" + value + "'");
         }
      }

      private void emitMimeType() {
         this.parsed = this.buildMimeType();
      }

      private MimeType buildMimeType() {
         if ("*".equals(this.type) && !"*".equals(this.subtype)) {
            throw new InvalidMimeTypeException(this.input, "wildcard type is legal only in '*/*' (all mime types)");
         }

         try {
            return new MimeType(this.type, this.subtype, this.parameters);
         } catch (UnsupportedCharsetException ex) {
            throw new InvalidMimeTypeException(this.input, "unsupported charset '" + ex.getCharsetName() + "'");
         } catch (IllegalArgumentException ex) {
            throw new InvalidMimeTypeException(this.input, ex.getMessage());
         }
      }
   }

   enum ParserState {
      INITIAL {
         @Override
         MimeTypeUtils.ParserState process(char c, MimeTypeUtils.MimeTypeParser parser) {
            if (c != ' ' && c != '\t') {
               parser.mark = parser.index;
               return TYPE;
            } else {
               return this;
            }
         }
      },
      TYPE {
         @Override
         MimeTypeUtils.ParserState process(char c, MimeTypeUtils.MimeTypeParser parser) {
            if (c == '/') {
               parser.type = parser.input.substring(parser.mark, parser.index);
               if (parser.type.isEmpty()) {
                  throw new InvalidMimeTypeException(parser.input, "'type' must not be empty");
               }

               parser.mark = parser.index + 1;
               return SUBTYPE;
            } else {
               if (c != ';' && c != ' ' && c != '\t') {
                  return this;
               }

               parser.resolveBareType(parser.input.substring(parser.mark, parser.index));
               return WHITESPACE;
            }
         }

         @Override
         void onEof(MimeTypeUtils.MimeTypeParser parser) {
            parser.resolveBareType(parser.input.substring(parser.mark));
            parser.emitMimeType();
         }
      },
      SUBTYPE {
         @Override
         MimeTypeUtils.ParserState process(char c, MimeTypeUtils.MimeTypeParser parser) {
            if (c != ';' && c != ' ' && c != '\t') {
               return this;
            }

            parser.subtype = parser.input.substring(parser.mark, parser.index);
            return WHITESPACE;
         }

         @Override
         void onEof(MimeTypeUtils.MimeTypeParser parser) {
            parser.subtype = parser.input.substring(parser.mark);
            parser.emitMimeType();
         }
      },
      WHITESPACE {
         @Override
         MimeTypeUtils.ParserState process(char c, MimeTypeUtils.MimeTypeParser parser) {
            if (c != ' ' && c != '\t' && c != ';') {
               parser.mark = parser.index;
               return PARAM_NAME;
            } else {
               return this;
            }
         }

         @Override
         void onEof(MimeTypeUtils.MimeTypeParser parser) {
            if (!parser.type.isEmpty()) {
               parser.emitMimeType();
            }
         }
      },
      PARAM_NAME {
         @Override
         MimeTypeUtils.ParserState process(char c, MimeTypeUtils.MimeTypeParser parser) {
            if (c == '=') {
               parser.paramName = parser.input.substring(parser.mark, parser.index);
               return PARAM_VALUE_START;
            }

            if (c != ' ' && c != '\t') {
               return this;
            }

            parser.paramName = parser.input.substring(parser.mark, parser.index);
            return PARAM_NAME_END;
         }
      },
      PARAM_NAME_END {
         @Override
         MimeTypeUtils.ParserState process(char c, MimeTypeUtils.MimeTypeParser parser) {
            if (c == ' ' || c == '\t') {
               return this;
            } else if (c == '=') {
               return PARAM_VALUE_START;
            } else {
               throw new InvalidMimeTypeException(parser.input, "Unexpected character '" + c + "' after parameter name");
            }
         }
      },
      PARAM_VALUE_START {
         @Override
         MimeTypeUtils.ParserState process(char c, MimeTypeUtils.MimeTypeParser parser) {
            if (c == ' ' || c == '\t') {
               return this;
            } else if (c == '"') {
               parser.mark = parser.index;
               return PARAM_VALUE_QUOTED;
            } else {
               parser.mark = parser.index;
               return PARAM_VALUE_TOKEN;
            }
         }
      },
      PARAM_VALUE_TOKEN {
         @Override
         MimeTypeUtils.ParserState process(char c, MimeTypeUtils.MimeTypeParser parser) {
            if (c != ';' && c != ' ' && c != '\t') {
               return this;
            }

            MimeTypeUtils.ParserState.extractParameter(parser, parser.input.substring(parser.mark, parser.index));
            return WHITESPACE;
         }

         @Override
         void onEof(MimeTypeUtils.MimeTypeParser parser) {
            MimeTypeUtils.ParserState.extractParameter(parser, parser.input.substring(parser.mark));
            parser.emitMimeType();
         }
      },
      PARAM_VALUE_QUOTED {
         @Override
         MimeTypeUtils.ParserState process(char c, MimeTypeUtils.MimeTypeParser parser) {
            if (c == '"') {
               MimeTypeUtils.ParserState.extractParameter(parser, parser.input.substring(parser.mark, parser.index + 1));
               return WHITESPACE;
            } else {
               return c == '\\' ? PARAM_VALUE_ESCAPED : this;
            }
         }

         @Override
         void onEof(MimeTypeUtils.MimeTypeParser parser) {
            MimeTypeUtils.ParserState.extractParameter(parser, parser.input.substring(parser.mark));
            parser.emitMimeType();
         }
      },
      PARAM_VALUE_ESCAPED {
         @Override
         MimeTypeUtils.ParserState process(char c, MimeTypeUtils.MimeTypeParser parser) {
            return PARAM_VALUE_QUOTED;
         }

         @Override
         void onEof(MimeTypeUtils.MimeTypeParser parser) {
            MimeTypeUtils.ParserState.extractParameter(parser, parser.input.substring(parser.mark));
            parser.emitMimeType();
         }
      };

      private static void extractParameter(MimeTypeUtils.MimeTypeParser parser, String input) {
         Assert.hasText(parser.paramName, "'paramName' must not be empty");
         parser.putParameter(parser.paramName, input);
      }

      abstract MimeTypeUtils.ParserState process(char c, MimeTypeUtils.MimeTypeParser parser);

      void onEof(MimeTypeUtils.MimeTypeParser parser) {
      }
   }
}
