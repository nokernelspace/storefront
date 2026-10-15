package org.springframework.web.util;

import java.util.Locale;
import java.util.Set;
import org.apache.commons.logging.Log;
import org.jspecify.annotations.Nullable;
import org.springframework.core.log.LogDelegateFactory;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

abstract class RfcUriParser {
   private static final Log logger = LogDelegateFactory.getHiddenLog(RfcUriParser.class);

   public static RfcUriParser.UriRecord parse(String uri) {
      return new RfcUriParser.InternalParser(uri).parse();
   }

   private static void verify(boolean expression, RfcUriParser.InternalParser parser, String message) {
      if (!expression) {
         fail(parser, message);
      }
   }

   private static void verifyIsHexDigit(char c, RfcUriParser.InternalParser parser, String message) {
      verify(c >= 'a' && c <= 'f' || c >= 'A' && c <= 'F' || c >= '0' && c <= '9', parser, message);
   }

   private static void fail(RfcUriParser.InternalParser parser, String message) {
      if (logger.isTraceEnabled()) {
         logger.trace(InvalidUrlException.class.getSimpleName() + ": \"" + message + "\" " + parser);
      }

      throw new InvalidUrlException(message);
   }

   private static class InternalParser {
      private static final Set<String> hierarchicalSchemes = Set.of("ftp", "file", "http", "https", "ws", "wss");
      private final String uri;
      private @Nullable String scheme;
      @Nullable String user;
      private @Nullable String host;
      private @Nullable String path;
      @Nullable String port;
      @Nullable String query;
      @Nullable String fragment;
      private RfcUriParser.State state = RfcUriParser.State.START;
      private int index;
      private int componentIndex;
      boolean isOpaque;
      private int remainingPercentEncodedChars;
      private boolean inUtf16Sequence;
      private boolean inPassword;
      private int openCurlyBracketCount;

      public InternalParser(String uri) {
         this.uri = uri;
      }

      public boolean hasScheme() {
         return this.scheme != null;
      }

      public boolean isOpaque() {
         return this.isOpaque;
      }

      public boolean hasUser() {
         return this.user != null;
      }

      public boolean hasHost() {
         return this.host != null;
      }

      public boolean isAtStartOfComponent() {
         return this.index == this.componentIndex;
      }

      public RfcUriParser.UriRecord parse() {
         Assert.isTrue(this.state == RfcUriParser.State.START && this.index == 0, "Internal Error");

         while (this.hasNext()) {
            this.state.handleNext(this, this.charAtIndex(), this.index);
            this.index++;
         }

         this.state.handleEnd(this);
         return new RfcUriParser.UriRecord(this.scheme, this.isOpaque, this.user, this.host, this.port, this.path, this.query, this.fragment);
      }

      public boolean hasNext() {
         return this.index < this.uri.length();
      }

      public char charAtIndex() {
         return this.uri.charAt(this.index);
      }

      public void advanceTo(RfcUriParser.State state) {
         if (RfcUriParser.logger.isTraceEnabled()) {
            RfcUriParser.logger.trace(this.state + " -> " + state + ", index=" + this.index + ", componentIndex=" + this.componentIndex);
         }

         this.state = state;
         this.openCurlyBracketCount = 0;
      }

      public void advanceTo(RfcUriParser.State state, int componentIndex) {
         this.componentIndex = componentIndex;
         this.advanceTo(state);
      }

      public RfcUriParser.InternalParser componentIndex(int componentIndex) {
         this.componentIndex = componentIndex;
         return this;
      }

      public void index(int index) {
         this.index = index;
      }

      public RfcUriParser.InternalParser resolveIfOpaque() {
         this.isOpaque = this.uri.charAt(this.index) != '/' && !hierarchicalSchemes.contains(this.scheme);
         return this;
      }

      public RfcUriParser.InternalParser captureScheme() {
         String scheme = this.captureComponent("scheme");
         this.scheme = !scheme.contains("{") ? scheme.toLowerCase(Locale.ROOT) : scheme;
         return this;
      }

      public RfcUriParser.InternalParser captureUser() {
         this.inPassword = false;
         this.user = this.captureComponent("user");
         return this;
      }

      public RfcUriParser.InternalParser captureHost() {
         RfcUriParser.verify(this.remainingPercentEncodedChars == 0 && !this.inPassword, this, "Bad authority");
         this.host = this.captureComponent("host");
         return this;
      }

      public RfcUriParser.InternalParser captureHostIfNotEmpty() {
         if (this.index > this.componentIndex) {
            this.captureHost();
         }

         return this;
      }

      public RfcUriParser.InternalParser capturePort() {
         RfcUriParser.verify(this.openCurlyBracketCount == 0, this, "Bad authority");
         String value = this.captureComponent("port");
         this.port = StringUtils.hasText(value) ? value : null;
         return this;
      }

      public RfcUriParser.InternalParser capturePath() {
         this.path = this.captureComponent("path");
         return this;
      }

      public RfcUriParser.InternalParser captureQuery() {
         this.query = this.captureComponent("query");
         return this;
      }

      public void captureFragmentIfNotEmpty() {
         if (this.index > this.componentIndex) {
            this.fragment = this.captureComponent("fragment");
         }
      }

      public RfcUriParser.InternalParser switchPortForFullPassword() {
         this.user = this.host + ":" + this.captureComponent();
         if (RfcUriParser.logger.isTraceEnabled()) {
            RfcUriParser.logger.trace("Switching from host/port to user=" + this.user);
         }

         return this;
      }

      public RfcUriParser.InternalParser switchPortForPassword() {
         this.inPassword = true;
         if (this.host != null) {
            this.componentIndex = this.componentIndex - this.host.length() - 1;
            this.host = null;
            if (RfcUriParser.logger.isTraceEnabled()) {
               RfcUriParser.logger.trace("Switching from host/port to username/password");
            }
         }

         return this;
      }

      private String captureComponent(String logPrefix) {
         String value = this.captureComponent();
         if (RfcUriParser.logger.isTraceEnabled()) {
            RfcUriParser.logger.trace(logPrefix + " set to '" + value + "'");
         }

         return value;
      }

      private String captureComponent() {
         return this.uri.substring(this.componentIndex, this.index);
      }

      public RfcUriParser.InternalParser markPercentEncoding() {
         RfcUriParser.verify(this.remainingPercentEncodedChars == 0, this, "Bad encoding");
         this.remainingPercentEncodedChars = 2;
         this.inUtf16Sequence = false;
         return this;
      }

      public boolean countDownPercentEncodingInHost(char c) {
         if (this.remainingPercentEncodedChars == 0) {
            return false;
         }

         this.remainingPercentEncodedChars--;
         RfcUriParser.verifyIsHexDigit(c, this, "Bad authority");
         return true;
      }

      public boolean countDownPercentEncodingInPath(char c) {
         if (this.remainingPercentEncodedChars == 0) {
            return false;
         } else if (this.remainingPercentEncodedChars == 2 && c == 'u' && !this.inUtf16Sequence) {
            this.inUtf16Sequence = true;
            this.remainingPercentEncodedChars = 4;
            return true;
         } else {
            this.remainingPercentEncodedChars--;
            RfcUriParser.verifyIsHexDigit(c, this, "Bad path");
            this.inUtf16Sequence = this.inUtf16Sequence & this.remainingPercentEncodedChars > 0;
            return true;
         }
      }

      public boolean processCurlyBrackets(char c) {
         if (c == '{') {
            this.openCurlyBracketCount++;
            return true;
         }

         if (c == '}') {
            if (this.openCurlyBracketCount > 0) {
               this.openCurlyBracketCount--;
               return true;
            } else {
               return false;
            }
         } else {
            return this.openCurlyBracketCount > 0;
         }
      }

      @Override
      public String toString() {
         return "[State="
            + this.state
            + ", index="
            + this.index
            + ", componentIndex="
            + this.componentIndex
            + ", uri='"
            + this.uri
            + "', scheme='"
            + this.scheme
            + "', user='"
            + this.user
            + "', host='"
            + this.host
            + "', path='"
            + this.path
            + "', port='"
            + this.port
            + "', query='"
            + this.query
            + "', fragment='"
            + this.fragment
            + "']";
      }
   }

   private enum State {
      START {
         @Override
         public void handleNext(RfcUriParser.InternalParser parser, char c, int i) {
            switch (c) {
               case '#':
                  parser.advanceTo(FRAGMENT, i + 1);
                  break;
               case '%':
                  parser.markPercentEncoding().advanceTo(PATH, i);
                  break;
               case '*':
                  parser.advanceTo(WILDCARD);
                  break;
               case '.':
               case ';':
                  parser.advanceTo(PATH, i);
                  break;
               case '/':
                  parser.advanceTo(HOST_OR_PATH, i);
                  break;
               case '?':
                  parser.advanceTo(QUERY, i + 1);
                  break;
               default:
                  if (parser.hasScheme()) {
                     parser.resolveIfOpaque().advanceTo(PATH, i);
                  } else {
                     parser.advanceTo(SCHEME_OR_PATH, i);
                  }
            }
         }

         @Override
         public void handleEnd(RfcUriParser.InternalParser parser) {
            parser.capturePath();
         }
      },
      HOST_OR_PATH {
         @Override
         public void handleNext(RfcUriParser.InternalParser parser, char c, int i) {
            switch (c) {
               case '#':
               case '%':
               case '.':
               case ';':
               case '?':
               case '@':
                  parser.index(--i);
                  parser.advanceTo(PATH);
                  break;
               case '/':
                  parser.componentIndex(i).captureHost().advanceTo(HOST, i + 1);
                  break;
               default:
                  parser.advanceTo(PATH);
            }
         }

         @Override
         public void handleEnd(RfcUriParser.InternalParser parser) {
            parser.capturePath();
         }
      },
      SCHEME_OR_PATH {
         @Override
         public void handleNext(RfcUriParser.InternalParser parser, char c, int i) {
            switch (c) {
               case '#':
                  parser.capturePath().advanceTo(FRAGMENT, i + 1);
                  break;
               case '%':
                  parser.markPercentEncoding().advanceTo(PATH);
                  break;
               case '/':
               case ';':
                  parser.advanceTo(PATH);
                  break;
               case ':':
                  parser.captureScheme().advanceTo(START);
                  break;
               case '?':
                  parser.capturePath().advanceTo(QUERY, i + 1);
            }
         }

         @Override
         public void handleEnd(RfcUriParser.InternalParser parser) {
            parser.capturePath();
         }
      },
      HOST {
         @Override
         public void handleNext(RfcUriParser.InternalParser parser, char c, int i) {
            switch (c) {
               case '#':
                  parser.captureHostIfNotEmpty().advanceTo(FRAGMENT, i + 1);
                  break;
               case '%':
                  parser.markPercentEncoding();
                  break;
               case '/':
                  parser.captureHost().advanceTo(PATH, i);
                  break;
               case ':':
                  parser.captureHostIfNotEmpty().advanceTo(PORT, i + 1);
                  break;
               case '?':
                  parser.captureHostIfNotEmpty().advanceTo(QUERY, i + 1);
                  break;
               case '@':
                  parser.captureUser().componentIndex(i + 1);
                  break;
               case '[':
                  RfcUriParser.verify(parser.isAtStartOfComponent(), parser, "Bad authority");
                  parser.advanceTo(IPV6);
                  break;
               default:
                  boolean isAllowed = parser.processCurlyBrackets(c)
                     || parser.countDownPercentEncodingInHost(c)
                     || HierarchicalUriComponents.Type.URI.isUnreservedOrSubDelimiter(c);
                  RfcUriParser.verify(isAllowed, parser, "Bad authority");
            }
         }

         @Override
         public void handleEnd(RfcUriParser.InternalParser parser) {
            parser.captureHostIfNotEmpty();
         }
      },
      IPV6 {
         @Override
         public void handleNext(RfcUriParser.InternalParser parser, char c, int i) {
            switch (c) {
               case ':':
                  break;
               case ']':
                  parser.index(++i);
                  parser.captureHost();
                  if (parser.hasNext()) {
                     char next = parser.charAtIndex();
                     if (next == ':') {
                        parser.advanceTo(PORT, i + 1);
                     } else if (next == '/') {
                        parser.advanceTo(PATH, i);
                     } else {
                        RfcUriParser.fail(parser, "Bad authority");
                     }
                  }
                  break;
               default:
                  RfcUriParser.verifyIsHexDigit(c, parser, "Bad authority");
            }
         }

         @Override
         public void handleEnd(RfcUriParser.InternalParser parser) {
            RfcUriParser.verify(parser.hasHost(), parser, "Bad authority");
         }
      },
      PORT {
         @Override
         public void handleNext(RfcUriParser.InternalParser parser, char c, int i) {
            if (c == '@') {
               RfcUriParser.verify(!parser.hasUser(), parser, "Bad authority");
               parser.switchPortForFullPassword().advanceTo(HOST, i + 1);
            } else if (c == '/') {
               parser.capturePort().advanceTo(PATH, i);
            } else if (c == '?' || c == '#') {
               parser.capturePort().advanceTo(c == '?' ? QUERY : FRAGMENT, i + 1);
            } else if (!Character.isDigit(c)) {
               if (parser.processCurlyBrackets(c)) {
                  return;
               }

               if (HierarchicalUriComponents.Type.URI.isUnreservedOrSubDelimiter(c) || c == '%') {
                  parser.switchPortForPassword().advanceTo(HOST);
                  return;
               }

               RfcUriParser.fail(parser, "Bad authority");
            }
         }

         @Override
         public void handleEnd(RfcUriParser.InternalParser parser) {
            parser.capturePort();
         }
      },
      PATH {
         @Override
         public void handleNext(RfcUriParser.InternalParser parser, char c, int i) {
            if (!parser.countDownPercentEncodingInPath(c)) {
               switch (c) {
                  case '#':
                     parser.capturePath().advanceTo(FRAGMENT, i + 1);
                     break;
                  case '%':
                     parser.markPercentEncoding();
                     break;
                  case '?':
                     if (!parser.isOpaque()) {
                        parser.capturePath().advanceTo(QUERY, i + 1);
                     }
               }
            }
         }

         @Override
         public void handleEnd(RfcUriParser.InternalParser parser) {
            parser.capturePath();
         }
      },
      QUERY {
         @Override
         public void handleNext(RfcUriParser.InternalParser parser, char c, int i) {
            if (c == '#') {
               parser.captureQuery().advanceTo(FRAGMENT, i + 1);
            }
         }

         @Override
         public void handleEnd(RfcUriParser.InternalParser parser) {
            parser.captureQuery();
         }
      },
      FRAGMENT {
         @Override
         public void handleNext(RfcUriParser.InternalParser parser, char c, int i) {
         }

         @Override
         public void handleEnd(RfcUriParser.InternalParser parser) {
            parser.captureFragmentIfNotEmpty();
         }
      },
      WILDCARD {
         @Override
         public void handleNext(RfcUriParser.InternalParser parser, char c, int i) {
            RfcUriParser.fail(parser, "Bad character '*'");
         }

         @Override
         public void handleEnd(RfcUriParser.InternalParser parser) {
            parser.capturePath();
         }
      };

      public abstract void handleNext(RfcUriParser.InternalParser parser, char c, int i);

      public abstract void handleEnd(RfcUriParser.InternalParser parser);
   }

   record UriRecord(
      @Nullable String scheme,
      boolean isOpaque,
      @Nullable String user,
      @Nullable String host,
      @Nullable String port,
      @Nullable String path,
      @Nullable String query,
      @Nullable String fragment
   ) {
   }
}
