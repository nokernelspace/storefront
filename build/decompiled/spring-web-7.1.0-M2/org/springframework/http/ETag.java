package org.springframework.http;

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

public record ETag(String tag, boolean weak) {
   private static final Log logger = LogFactory.getLog(ETag.class);
   private static final ETag WILDCARD = new ETag("*", false);

   public boolean isWildcard() {
      return this == WILDCARD;
   }

   public boolean compare(ETag other, boolean strong) {
      if (StringUtils.hasLength(this.tag()) && StringUtils.hasLength(other.tag())) {
         return !strong || !this.weak() && !other.weak() ? this.tag().equals(other.tag()) : false;
      } else {
         return false;
      }
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof ETag oet && this.tag.equals(oet.tag) && this.weak == oet.weak;
   }

   @Override
   public int hashCode() {
      int result = this.tag.hashCode();
      return 31 * result + Boolean.hashCode(this.weak);
   }

   @Override
   public String toString() {
      return this.formattedTag();
   }

   public String formattedTag() {
      return this.isWildcard() ? "*" : (this.weak ? "W/" : "") + "\"" + this.tag + "\"";
   }

   public static ETag create(String rawValue) {
      boolean weak = rawValue.startsWith("W/");
      if (weak) {
         rawValue = rawValue.substring(2);
      }

      if (rawValue.length() > 2 && rawValue.startsWith("\"") && rawValue.endsWith("\"")) {
         rawValue = rawValue.substring(1, rawValue.length() - 1);
      }

      return new ETag(rawValue, weak);
   }

   public static List<ETag> parse(String source) {
      List<ETag> result = new ArrayList<>();
      ETag.State state = ETag.State.BEFORE_QUOTES;
      int startIndex = -1;
      boolean weak = false;

      for (int i = 0; i < source.length(); i++) {
         char c = source.charAt(i);
         if (state == ETag.State.IN_QUOTES) {
            if (c == '"') {
               String tag = source.substring(startIndex, i);
               if (StringUtils.hasText(tag)) {
                  result.add(new ETag(tag, weak));
               }

               state = ETag.State.AFTER_QUOTES;
               startIndex = -1;
               weak = false;
            }
         } else if (!Character.isWhitespace(c)) {
            if (c == ',') {
               state = ETag.State.BEFORE_QUOTES;
            } else {
               if (state == ETag.State.BEFORE_QUOTES) {
                  if (c == '*') {
                     result.add(WILDCARD);
                     state = ETag.State.AFTER_QUOTES;
                     continue;
                  }

                  if (c == '"') {
                     state = ETag.State.IN_QUOTES;
                     startIndex = i + 1;
                     continue;
                  }

                  if (c == 'W' && source.length() > i + 2 && source.charAt(i + 1) == '/' && source.charAt(i + 2) == '"') {
                     state = ETag.State.IN_QUOTES;
                     i += 2;
                     startIndex = i + 1;
                     weak = true;
                     continue;
                  }
               }

               if (logger.isDebugEnabled()) {
                  logger.debug("Unexpected char at index " + i);
               }
            }
         }
      }

      if (state != ETag.State.IN_QUOTES && logger.isDebugEnabled()) {
         logger.debug("Expected closing '\"'");
      }

      return result;
   }

   public static String quoteETagIfNecessary(String tag) {
      if (tag.startsWith("W/\"")) {
         if (tag.length() > 3 && tag.endsWith("\"")) {
            return tag;
         }
      } else if (tag.startsWith("\"") && tag.length() > 1 && tag.endsWith("\"")) {
         return tag;
      }

      return "\"" + tag + "\"";
   }

   private enum State {
      BEFORE_QUOTES,
      IN_QUOTES,
      AFTER_QUOTES;
   }
}
