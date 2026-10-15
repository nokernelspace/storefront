package org.springframework.web.util;

import java.net.IDN;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntPredicate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

final class WhatWgUrlParser {
   public static final WhatWgUrlParser.UrlRecord EMPTY_RECORD = new WhatWgUrlParser.UrlRecord();
   private static final Log logger = LogFactory.getLog(WhatWgUrlParser.class);
   private static final int EOF = -1;
   private static final int MAX_PORT = 65535;
   private final StringBuilder input;
   private final WhatWgUrlParser.@Nullable UrlRecord base;
   private @Nullable Charset encoding;
   private final @Nullable Consumer<String> validationErrorHandler;
   private int pointer;
   private final StringBuilder buffer;
   private WhatWgUrlParser.@Nullable State state;
   private WhatWgUrlParser.@Nullable State stateOverride;
   private boolean atSignSeen;
   private boolean passwordTokenSeen;
   private boolean insideBrackets;
   private int openCurlyBracketCount;
   private boolean stopMainLoop = false;

   private WhatWgUrlParser(
      String input, WhatWgUrlParser.@Nullable UrlRecord base, @Nullable Charset encoding, @Nullable Consumer<String> validationErrorHandler
   ) {
      this.input = new StringBuilder(input);
      this.base = base;
      this.encoding = encoding;
      this.validationErrorHandler = validationErrorHandler;
      this.buffer = new StringBuilder(this.input.length() / 2);
   }

   public static WhatWgUrlParser.UrlRecord parse(
      String input, WhatWgUrlParser.@Nullable UrlRecord base, @Nullable Charset encoding, @Nullable Consumer<String> validationErrorHandler
   ) throws InvalidUrlException {
      Assert.notNull(input, "Input must not be null");
      WhatWgUrlParser parser = new WhatWgUrlParser(input, base, encoding, validationErrorHandler);
      return parser.basicUrlParser(null, null);
   }

   private WhatWgUrlParser.UrlRecord basicUrlParser(WhatWgUrlParser.@Nullable UrlRecord url, WhatWgUrlParser.@Nullable State stateOverride) {
      if (url == null) {
         url = new WhatWgUrlParser.UrlRecord();
         this.sanitizeInput(true);
      } else {
         this.sanitizeInput(false);
      }

      this.state = stateOverride != null ? stateOverride : WhatWgUrlParser.State.SCHEME_START;

      for (this.stateOverride = stateOverride; !this.stopMainLoop && this.pointer <= this.input.length(); this.pointer++) {
         int c;
         if (this.pointer < this.input.length()) {
            c = this.input.codePointAt(this.pointer);
         } else {
            c = -1;
         }

         if (logger.isTraceEnabled()) {
            logger.trace(
               "current: " + (c != -1 ? Character.toString(c) : "EOF") + " ptr: " + this.pointer + " Buffer: " + this.buffer + " State: " + this.state
            );
         }

         this.state.handle(c, url, this);
      }

      return url;
   }

   void sanitizeInput(boolean removeC0ControlOrSpace) {
      boolean strip = true;

      for (int i = 0; i < this.input.length(); i++) {
         int c = this.input.codePointAt(i);
         boolean isSpaceOrC0 = c == 32 || isC0Control(c);
         boolean isTabOrNL = c == 9 || isNewline(c);
         if ((!strip || !isSpaceOrC0) && !isTabOrNL) {
            strip = false;
         } else {
            if (this.validate()) {
               this.validationError("Code point \"" + c + "\" is not a URL unit.");
            }

            if (removeC0ControlOrSpace && isSpaceOrC0) {
               this.input.deleteCharAt(i);
            } else if (isTabOrNL) {
               this.input.deleteCharAt(i);
            }

            i--;
         }
      }

      if (removeC0ControlOrSpace) {
         for (int i = this.input.length() - 1; i >= 0; i--) {
            int c = this.input.codePointAt(i);
            if (c != 32 && !isC0Control(c)) {
               break;
            }

            if (this.validate()) {
               this.validationError("Code point \"" + c + "\" is not a URL unit.");
            }

            this.input.deleteCharAt(i);
         }
      }
   }

   private void setState(WhatWgUrlParser.State newState) {
      if (logger.isTraceEnabled()) {
         String c;
         if (this.pointer < this.input.length()) {
            c = Character.toString(this.input.codePointAt(this.pointer));
         } else {
            c = "EOF";
         }

         logger.trace("Changing state from " + this.state + " to " + newState + " (cur: " + c + ")");
      }

      this.state = newState;
      this.openCurlyBracketCount = this.buffer.toString().equals("{") ? this.openCurlyBracketCount : 0;
   }

   private boolean processCurlyBrackets(int c) {
      if (c == 123) {
         this.openCurlyBracketCount++;
         return true;
      }

      if (c == 125) {
         if (this.openCurlyBracketCount > 0) {
            this.openCurlyBracketCount--;
            return true;
         } else {
            return false;
         }
      } else {
         return this.openCurlyBracketCount > 0 && c != -1;
      }
   }

   private static LinkedList<String> strictSplit(String input, int delimiter) {
      int position = 0;
      LinkedList<String> tokens = new LinkedList<>();
      int delIdx = input.indexOf(delimiter, position);
      String token = delIdx != -1 ? input.substring(position, delIdx) : input.substring(position);
      position = delIdx;
      tokens.add(token);

      while (position != -1) {
         Assert.state(input.codePointAt(position) == delimiter, "Codepoint is not a delimiter");
         delIdx = input.indexOf(delimiter, ++position);
         token = delIdx != -1 ? input.substring(position, delIdx) : input.substring(position);
         position = delIdx;
         tokens.add(token);
      }

      return tokens;
   }

   private static String domainToAscii(String domain, boolean beStrict) {
      if (!beStrict && containsOnlyAscii(domain)) {
         return domain.toLowerCase(Locale.ENGLISH);
      }

      int flag = 0;
      if (beStrict) {
         flag |= 2;
      }

      try {
         return IDN.toASCII(domain, flag);
      } catch (IllegalArgumentException ex) {
         throw new InvalidUrlException("Could not convert \"" + domain + "\" to ASCII: " + ex.getMessage(), ex);
      }
   }

   private boolean validate() {
      return this.validationErrorHandler != null;
   }

   private void validationError(@Nullable String additionalInfo) {
      if (this.validationErrorHandler != null) {
         StringBuilder message = new StringBuilder("URL validation error for URL [");
         message.append(this.input);
         message.append("]@");
         message.append(this.pointer);
         if (additionalInfo != null) {
            message.append(". ");
            message.append(additionalInfo);
         }

         this.validationErrorHandler.accept(message.toString());
      }
   }

   private void failure(@Nullable String additionalInfo) {
      StringBuilder message = new StringBuilder("URL parsing failure for URL [");
      message.append(this.input);
      message.append("] @ ");
      message.append(this.pointer);
      if (additionalInfo != null) {
         message.append(". ");
         message.append(additionalInfo);
      }

      throw new InvalidUrlException(message.toString());
   }

   private static boolean c0ControlPercentEncodeSet(int ch) {
      return isC0Control(ch) || Integer.compareUnsigned(ch, 126) > 0;
   }

   private static boolean fragmentPercentEncodeSet(int ch) {
      return c0ControlPercentEncodeSet(ch) || ch == 32 || ch == 34 || ch == 60 || ch == 62 || ch == 96;
   }

   private static boolean queryPercentEncodeSet(int ch) {
      return c0ControlPercentEncodeSet(ch) || ch == 32 || ch == 34 || ch == 35 || ch == 60 || ch == 62;
   }

   private static boolean specialQueryPercentEncodeSet(int ch) {
      return queryPercentEncodeSet(ch) || ch == 39;
   }

   private static boolean pathPercentEncodeSet(int ch) {
      return queryPercentEncodeSet(ch) || ch == 63 || ch == 96 || ch == 123 || ch == 125;
   }

   private static boolean userinfoPercentEncodeSet(int ch) {
      return pathPercentEncodeSet(ch)
         || ch == 47
         || ch == 58
         || ch == 59
         || ch == 61
         || ch == 64
         || Integer.compareUnsigned(ch, 91) >= 0 && Integer.compareUnsigned(ch, 94) <= 0
         || ch == 124;
   }

   private static boolean isC0Control(int ch) {
      return ch >= 0 && ch <= 31;
   }

   private static boolean isNewline(int ch) {
      return ch == 13 || ch == 10;
   }

   private static boolean isAsciiAlpha(int ch) {
      return ch >= 65 && ch <= 90 || ch >= 97 && ch <= 122;
   }

   private static boolean containsOnlyAsciiDigits(CharSequence string) {
      for (int i = 0; i < string.length(); i++) {
         int ch = codePointAt(string, i);
         if (!isAsciiDigit(ch)) {
            return false;
         }
      }

      return true;
   }

   private static boolean containsOnlyAscii(String string) {
      for (int i = 0; i < string.length(); i++) {
         int ch = string.codePointAt(i);
         if (!isAsciiCodePoint(ch)) {
            return false;
         }
      }

      return true;
   }

   private static boolean isAsciiCodePoint(int ch) {
      return Integer.compareUnsigned(ch, 0) >= 0 && Integer.compareUnsigned(ch, 127) <= 0;
   }

   private static boolean isAsciiDigit(int ch) {
      return ch >= 48 && ch <= 57;
   }

   private static boolean isAsciiAlphaNumeric(int ch) {
      return isAsciiAlpha(ch) || isAsciiDigit(ch);
   }

   private static boolean isAsciiHexDigit(int ch) {
      return isAsciiDigit(ch) || ch >= 65 && ch <= 70 || ch >= 97 && ch <= 102;
   }

   private static boolean isForbiddenDomain(int ch) {
      return isForbiddenHost(ch) || isC0Control(ch) || ch == 37 || ch == 127;
   }

   private static boolean isForbiddenHost(int ch) {
      return ch == 0
         || ch == 9
         || isNewline(ch)
         || ch == 32
         || ch == 35
         || ch == 47
         || ch == 58
         || ch == 60
         || ch == 62
         || ch == 63
         || ch == 64
         || ch == 91
         || ch == 92
         || ch == 93
         || ch == 94
         || ch == 124;
   }

   private static boolean isNonCharacter(int ch) {
      return ch >= 64976 && ch <= 65007
         || ch == 65534
         || ch == 65535
         || ch == 131070
         || ch == 131071
         || ch == 196606
         || ch == 196607
         || ch == 262142
         || ch == 262143
         || ch == 327678
         || ch == 327679
         || ch == 393214
         || ch == 393215
         || ch == 458750
         || ch == 458751
         || ch == 524286
         || ch == 524287
         || ch == 589822
         || ch == 589823
         || ch == 655358
         || ch == 655359
         || ch == 720894
         || ch == 720895
         || ch == 786430
         || ch == 786431
         || ch == 851966
         || ch == 851967
         || ch == 917502
         || ch == 917503
         || ch == 983038
         || ch == 983039
         || ch == 1048574
         || ch == 1048575
         || ch == 1114110
         || ch == 1114111;
   }

   private static boolean isUrlCodePoint(int ch) {
      return isAsciiAlphaNumeric(ch)
         || ch == 33
         || ch == 36
         || ch == 38
         || ch == 39
         || ch == 40
         || ch == 41
         || ch == 42
         || ch == 43
         || ch == 44
         || ch == 45
         || ch == 46
         || ch == 47
         || ch == 58
         || ch == 59
         || ch == 61
         || ch == 63
         || ch == 64
         || ch == 95
         || ch == 126
         || ch >= 160 && ch <= 1114109 && !Character.isSurrogate((char)ch) && !isNonCharacter(ch);
   }

   private static boolean isSpecialScheme(String scheme) {
      return "ftp".equals(scheme) || "file".equals(scheme) || "http".equals(scheme) || "https".equals(scheme) || "ws".equals(scheme) || "wss".equals(scheme);
   }

   private static int defaultPort(@Nullable String scheme) {
      if (scheme != null) {
         return switch (scheme) {
            case "ftp" -> 21;
            case "http", "ws" -> 80;
            case "https", "wss" -> 443;
            default -> -1;
         };
      } else {
         return -1;
      }
   }

   private void append(String s) {
      this.buffer.append(s);
   }

   private void append(char ch) {
      this.buffer.append(ch);
   }

   private void append(int ch) {
      this.buffer.appendCodePoint(ch);
   }

   private void prepend(String s) {
      this.buffer.insert(0, s);
   }

   private void emptyBuffer() {
      this.buffer.setLength(0);
   }

   private int remaining(int deltaPos) {
      int pos = this.pointer + deltaPos + 1;
      return pos < this.input.length() ? this.input.codePointAt(pos) : -1;
   }

   private static String percentDecode(String input) {
      try {
         return UriUtils.decode(input, StandardCharsets.UTF_8);
      } catch (IllegalArgumentException ex) {
         throw new InvalidUrlException("Could not decode \"" + input + "\": " + ex.getMessage(), ex);
      }
   }

   private @Nullable String percentEncode(int c, IntPredicate percentEncodeSet) {
      return this.encoding == null ? null : this.percentEncode(Character.toString(c), percentEncodeSet);
   }

   private String percentEncode(String input, IntPredicate percentEncodeSet) {
      if (this.encoding == null) {
         return input;
      }

      byte[] bytes = input.getBytes(this.encoding);
      boolean original = true;

      for (byte b : bytes) {
         if (percentEncodeSet.test(b)) {
            original = false;
            break;
         }
      }

      if (original) {
         return input;
      }

      StringBuilder output = new StringBuilder();

      for (byte b : bytes) {
         if (!percentEncodeSet.test(b)) {
            output.append((char)b);
         } else {
            output.append('%');
            char hex1 = Character.toUpperCase(Character.forDigit(b >> 4 & 15, 16));
            char hex2 = Character.toUpperCase(Character.forDigit(b & 15, 16));
            output.append(hex1);
            output.append(hex2);
         }
      }

      return output.toString();
   }

   private static boolean isSingleDotPathSegment(StringBuilder b) {
      int len = b.length();
      switch (len) {
         case 1: {
            int ch0 = b.codePointAt(0);
            return ch0 == 46;
         }
         case 2: {
            int ch0 = b.codePointAt(0);
            int ch1 = b.codePointAt(1);
            return ch0 == 47 && ch1 == 46;
         }
         case 3: {
            int ch0 = b.codePointAt(0);
            int ch1 = b.codePointAt(1);
            int ch2 = b.codePointAt(2);
            return ch0 == 37 && ch1 == 50 && (ch2 == 101 || ch2 == 69);
         }
         case 4: {
            int ch0 = b.codePointAt(0);
            int ch1 = b.codePointAt(1);
            int ch2 = b.codePointAt(2);
            int ch3 = b.codePointAt(3);
            return ch0 == 47 && ch1 == 37 && ch2 == 50 && (ch3 == 101 || ch3 == 69);
         }
         default:
            return false;
      }
   }

   private static boolean isDoubleDotPathSegment(StringBuilder b) {
      int len = b.length();
      switch (len) {
         case 2: {
            int ch0 = b.codePointAt(0);
            int ch1 = b.codePointAt(1);
            return ch0 == 46 && ch1 == 46;
         }
         case 3: {
            int ch0 = b.codePointAt(0);
            int ch1 = b.codePointAt(1);
            int ch2 = b.codePointAt(2);
            return ch0 == 47 && ch1 == 46 && ch2 == 46;
         }
         case 4: {
            int ch0 = b.codePointAt(0);
            int ch1 = b.codePointAt(1);
            int ch2 = b.codePointAt(2);
            int ch3 = b.codePointAt(3);
            return ch0 == 46 && ch1 == 37 && ch2 == 50 && (ch3 == 101 || ch3 == 69) || ch0 == 37 && ch1 == 50 && (ch2 == 101 || ch2 == 69) && ch3 == 46;
         }
         case 5: {
            int ch0 = b.codePointAt(0);
            int ch1 = b.codePointAt(1);
            int ch2 = b.codePointAt(2);
            int ch3 = b.codePointAt(3);
            int ch4 = b.codePointAt(4);
            return ch0 == 47
               && (ch1 == 46 && ch2 == 37 && ch3 == 50 && (ch4 == 101 || ch4 == 69) || ch1 == 37 && ch2 == 50 && (ch3 == 101 || ch3 == 69) && ch4 == 46);
         }
         case 6: {
            int ch0 = b.codePointAt(0);
            int ch1 = b.codePointAt(1);
            int ch2 = b.codePointAt(2);
            int ch3 = b.codePointAt(3);
            int ch4 = b.codePointAt(4);
            int ch5 = b.codePointAt(5);
            return ch0 == 37 && ch1 == 50 && (ch2 == 101 || ch2 == 69) && ch3 == 37 && ch4 == 50 && (ch5 == 101 || ch5 == 69);
         }
         case 7: {
            int ch0 = b.codePointAt(0);
            int ch1 = b.codePointAt(1);
            int ch2 = b.codePointAt(2);
            int ch3 = b.codePointAt(3);
            int ch4 = b.codePointAt(4);
            int ch5 = b.codePointAt(5);
            int ch6 = b.codePointAt(6);
            return ch0 == 47 && ch1 == 37 && ch2 == 50 && (ch3 == 101 || ch3 == 69) && ch4 == 37 && ch5 == 50 && (ch6 == 101 || ch6 == 69);
         }
         default:
            return false;
      }
   }

   private static boolean isWindowsDriveLetter(CharSequence input, boolean normalized) {
      return input.length() != 2 ? false : isWindowsDriveLetterInternal(input, normalized);
   }

   private static boolean startsWithWindowsDriveLetter(String input) {
      int len = input.length();
      if (len < 2) {
         return false;
      }

      if (!isWindowsDriveLetterInternal(input, false)) {
         return false;
      }

      if (len == 2) {
         return true;
      }

      int ch2 = input.codePointAt(2);
      return ch2 == 47 || ch2 == 92 || ch2 == 63 || ch2 == 35;
   }

   private static boolean isWindowsDriveLetterInternal(CharSequence s, boolean normalized) {
      int ch0 = codePointAt(s, 0);
      if (!isAsciiAlpha(ch0)) {
         return false;
      }

      int ch1 = codePointAt(s, 1);
      return normalized ? ch1 == 58 : ch1 == 58 || ch1 == 124;
   }

   private static int codePointAt(CharSequence s, int index) {
      if (s instanceof String string) {
         return string.codePointAt(index);
      } else if (s instanceof StringBuilder builder) {
         return builder.codePointAt(index);
      } else {
         throw new IllegalStateException();
      }
   }

   static final class Domain implements WhatWgUrlParser.Host {
      private final String domain;

      Domain(String domain) {
         this.domain = domain;
      }

      public String domain() {
         return this.domain;
      }

      @Override
      public boolean equals(@Nullable Object o) {
         if (o == this) {
            return true;
         } else {
            return o instanceof WhatWgUrlParser.Domain other ? this.domain.equals(other.domain) : false;
         }
      }

      @Override
      public int hashCode() {
         return this.domain.hashCode();
      }

      @Override
      public String toString() {
         return this.domain;
      }
   }

   static final class EmptyHost implements WhatWgUrlParser.Host {
      static final WhatWgUrlParser.EmptyHost INSTANCE = new WhatWgUrlParser.EmptyHost();

      private EmptyHost() {
      }

      @Override
      public boolean equals(@Nullable Object obj) {
         return obj == this || obj != null && this.getClass() == obj.getClass();
      }

      @Override
      public int hashCode() {
         return 1;
      }

      @Override
      public String toString() {
         return "";
      }
   }

   sealed interface Host permits WhatWgUrlParser.Domain, WhatWgUrlParser.EmptyHost, WhatWgUrlParser.IpAddressHost, WhatWgUrlParser.OpaqueHost {
      static WhatWgUrlParser.Host parse(String input, boolean isOpaque, WhatWgUrlParser p) {
         if (!input.isEmpty() && input.codePointAt(0) == 91) {
            int last = input.length() - 1;
            if (input.codePointAt(last) != 93) {
               throw new InvalidUrlException("IPv6 address is missing the closing \"]\").");
            }

            String ipv6Host = input.substring(1, last);
            return new WhatWgUrlParser.IpAddressHost(WhatWgUrlParser.Ipv6Address.parse(ipv6Host));
         } else {
            if (isOpaque) {
               return WhatWgUrlParser.OpaqueHost.parse(input, p);
            }

            Assert.state(!input.isEmpty(), "Input should not be empty");
            String domain = WhatWgUrlParser.percentDecode(input);
            String asciiDomain = WhatWgUrlParser.domainToAscii(domain, false);

            for (int i = 0; i < asciiDomain.length(); i++) {
               int ch = asciiDomain.codePointAt(i);
               if (WhatWgUrlParser.isForbiddenDomain(ch)) {
                  throw new InvalidUrlException("Invalid character \"" + ch + "\" in domain \"" + input + "\"");
               }
            }

            if (endsInNumber(asciiDomain)) {
               WhatWgUrlParser.Ipv4Address address = WhatWgUrlParser.Ipv4Address.parse(asciiDomain, p);
               return new WhatWgUrlParser.IpAddressHost(address);
            } else {
               return new WhatWgUrlParser.Domain(asciiDomain);
            }
         }
      }

      private static boolean endsInNumber(String input) {
         LinkedList<String> parts = WhatWgUrlParser.strictSplit(input, 46);
         if (parts.isEmpty()) {
            return false;
         }

         if (parts.getLast().isEmpty()) {
            if (parts.size() == 1) {
               return false;
            }

            parts.removeLast();
         }

         String last = parts.getLast();
         if (!last.isEmpty() && WhatWgUrlParser.containsOnlyAsciiDigits(last)) {
            return true;
         }

         WhatWgUrlParser.ParseIpv4NumberResult result = WhatWgUrlParser.Ipv4Address.parseIpv4Number(last);
         return result != WhatWgUrlParser.ParseIpv4NumberFailure.INSTANCE;
      }
   }

   static final class IntPort implements WhatWgUrlParser.Port {
      private final int port;

      public IntPort(int port) {
         this.port = port;
      }

      public int value() {
         return this.port;
      }

      @Override
      public String toString() {
         return Integer.toString(this.port);
      }
   }

   sealed interface IpAddress permits WhatWgUrlParser.Ipv4Address, WhatWgUrlParser.Ipv6Address {
   }

   static final class IpAddressHost implements WhatWgUrlParser.Host {
      private final WhatWgUrlParser.IpAddress address;
      private final String addressString;

      IpAddressHost(WhatWgUrlParser.IpAddress address) {
         this.address = address;
         if (address instanceof WhatWgUrlParser.Ipv6Address) {
            this.addressString = "[" + address + "]";
         } else {
            this.addressString = address.toString();
         }
      }

      public WhatWgUrlParser.IpAddress address() {
         return this.address;
      }

      @Override
      public boolean equals(@Nullable Object obj) {
         if (obj == this) {
            return true;
         } else {
            return obj instanceof WhatWgUrlParser.IpAddressHost other ? this.address.equals(other.address) : false;
         }
      }

      @Override
      public int hashCode() {
         return this.address.hashCode();
      }

      @Override
      public String toString() {
         return this.addressString;
      }
   }

   static final class Ipv4Address implements WhatWgUrlParser.IpAddress {
      private final int address;
      private final String string;

      Ipv4Address(int address) {
         this.address = address;
         this.string = serialize(address);
      }

      private static String serialize(int address) {
         StringBuilder output = new StringBuilder();
         int n = address;

         for (int i = 1; i <= 4; i++) {
            output.insert(0, Integer.toUnsignedString(Integer.remainderUnsigned(n, 256)));
            if (i != 4) {
               output.insert(0, '.');
            }

            n = Math.floorDiv(n, 256);
         }

         return output.toString();
      }

      public static WhatWgUrlParser.Ipv4Address parse(String input, WhatWgUrlParser p) {
         List<String> parts = WhatWgUrlParser.strictSplit(input, 46);
         int partsSize = parts.size();
         if (parts.get(partsSize - 1).isEmpty()) {
            p.validationError("IPv4 address ends with \".\"");
            if (partsSize > 1) {
               parts.remove(partsSize - 1);
               partsSize--;
            }
         }

         if (partsSize > 4) {
            throw new InvalidUrlException("IPv4 address does not consist of exactly 4 parts.");
         }

         List<Integer> numbers = new ArrayList<>(partsSize);

         for (int i = 0; i < partsSize; i++) {
            String part = parts.get(i);
            WhatWgUrlParser.ParseIpv4NumberResult result = parseIpv4Number(part);
            if (result == WhatWgUrlParser.ParseIpv4NumberFailure.INSTANCE) {
               p.failure("An IPv4 address part is not numeric.");
            } else {
               WhatWgUrlParser.ParseIpv4NumberSuccess success = (WhatWgUrlParser.ParseIpv4NumberSuccess)result;
               if (p.validate() && success.validationError()) {
                  p.validationError("The IPv4 address contains numbers expressed using hexadecimal or octal digits.");
               }

               numbers.add(success.number());
            }
         }

         Iterator<Integer> iterator = numbers.iterator();

         while (iterator.hasNext()) {
            Integer number = iterator.next();
            if (p.validate() && number > 255) {
               p.validationError("An IPv4 address part exceeds 255.");
            }

            if (iterator.hasNext()) {
               if (number > 255) {
                  throw new InvalidUrlException("An IPv4 address part exceeds 255.");
               }
            } else {
               double limit = Math.pow(256.0, 5 - numbers.size());
               if (number.intValue() >= limit) {
                  throw new InvalidUrlException("IPv4 address part " + number + " exceeds " + limit + ".'");
               }
            }
         }

         int ipv4 = numbers.get(numbers.size() - 1);
         numbers.remove(numbers.size() - 1);
         int counter = 0;

         for (Integer n : numbers) {
            int increment = n * (int)Math.pow(256.0, 3 - counter);
            ipv4 += increment;
            counter++;
         }

         return new WhatWgUrlParser.Ipv4Address(ipv4);
      }

      private static WhatWgUrlParser.ParseIpv4NumberResult parseIpv4Number(String input) {
         if (input.isEmpty()) {
            return WhatWgUrlParser.ParseIpv4NumberFailure.INSTANCE;
         }

         boolean validationError = false;
         int r = 10;
         int len = input.length();
         if (len >= 2) {
            int ch0 = input.codePointAt(0);
            int ch1 = input.codePointAt(1);
            if (ch0 != 48 || ch1 != 88 && ch1 != 120) {
               if (ch0 == 48) {
                  validationError = true;
                  input = input.substring(1);
                  r = 8;
               }
            } else {
               validationError = true;
               input = input.substring(2);
               r = 16;
            }
         }

         if (input.isEmpty()) {
            return new WhatWgUrlParser.ParseIpv4NumberSuccess(0, true);
         }

         for (int i = 0; i < input.length(); i++) {
            int c = input.codePointAt(i);
            int digit = Character.digit(c, r);
            if (digit == -1) {
               return WhatWgUrlParser.ParseIpv4NumberFailure.INSTANCE;
            }
         }

         try {
            int output = Integer.parseInt(input, r);
            return new WhatWgUrlParser.ParseIpv4NumberSuccess(output, validationError);
         } catch (NumberFormatException ex) {
            return WhatWgUrlParser.ParseIpv4NumberFailure.INSTANCE;
         }
      }

      @Override
      public boolean equals(@Nullable Object o) {
         if (o == this) {
            return true;
         } else {
            return o instanceof WhatWgUrlParser.Ipv4Address other ? this.address == other.address : false;
         }
      }

      @Override
      public int hashCode() {
         return this.address;
      }

      @Override
      public String toString() {
         return this.string;
      }
   }

   static final class Ipv6Address implements WhatWgUrlParser.IpAddress {
      private final int[] pieces;
      private final String string;

      private Ipv6Address(int[] pieces) {
         Assert.state(pieces.length == 8, "Invalid amount of IPv6 pieces");
         this.pieces = pieces;
         this.string = serialize(pieces);
      }

      public static WhatWgUrlParser.Ipv6Address parse(String input) {
         int[] address = new int[8];
         int pieceIndex = 0;
         Integer compress = null;
         int pointer = 0;
         int inputLength = input.length();
         int c = inputLength > 0 ? input.codePointAt(0) : -1;
         if (c == 58) {
            if (inputLength > 1 && input.codePointAt(1) != 58) {
               throw new InvalidUrlException("IPv6 address begins with improper compression.");
            }

            pointer += 2;
            compress = ++pieceIndex;
         }

         c = pointer < inputLength ? input.codePointAt(pointer) : -1;

         while (c != -1) {
            if (pieceIndex == 8) {
               throw new InvalidUrlException("IPv6 address contains more than 8 pieces.");
            }

            if (c == 58) {
               if (compress != null) {
                  throw new InvalidUrlException("IPv6 address is compressed in more than one spot.");
               }

               pointer++;
               compress = ++pieceIndex;
               c = pointer < inputLength ? input.codePointAt(pointer) : -1;
            } else {
               int value = 0;

               int length;
               for (length = 0; length < 4 && WhatWgUrlParser.isAsciiHexDigit(c); c = pointer < inputLength ? input.codePointAt(pointer) : -1) {
                  int cHex = Character.digit(c, 16);
                  value = value * 16 + cHex;
                  pointer++;
                  length++;
               }

               if (c == 46) {
                  if (length == 0) {
                     throw new InvalidUrlException("IPv6 address with IPv4 address syntax: IPv4 part is empty.");
                  }

                  pointer -= length;
                  if (pieceIndex > 6) {
                     throw new InvalidUrlException("IPv6 address with IPv4 address syntax: IPv6 address has more than 6 pieces.");
                  }

                  int numbersSeen = 0;

                  for (int var15 = pointer < inputLength ? input.codePointAt(pointer) : -1;
                     var15 != -1;
                     var15 = pointer < inputLength ? input.codePointAt(pointer) : -1
                  ) {
                     Integer ipv4Piece = null;
                     if (numbersSeen > 0) {
                        if (var15 != 46 || numbersSeen >= 4) {
                           throw new InvalidUrlException("IPv6 address with IPv4 address syntax: IPv4 part is empty or contains a non-ASCII digit.");
                        }

                        pointer++;
                        var15 = pointer < inputLength ? input.codePointAt(pointer) : -1;
                     }

                     if (!WhatWgUrlParser.isAsciiDigit(var15)) {
                        throw new InvalidUrlException("IPv6 address with IPv4 address syntax: IPv4 part contains a non-ASCII digit.");
                     }

                     while (WhatWgUrlParser.isAsciiDigit(var15)) {
                        int number = Character.digit(var15, 10);
                        if (ipv4Piece == null) {
                           ipv4Piece = number;
                        } else {
                           if (ipv4Piece == 0) {
                              throw new InvalidUrlException("IPv6 address with IPv4 address syntax: IPv4 part contains a non-ASCII digit.");
                           }

                           ipv4Piece = ipv4Piece * 10 + number;
                        }

                        if (ipv4Piece > 255) {
                           throw new InvalidUrlException("IPv6 address with IPv4 address syntax: IPv4 part exceeds 255.");
                        }

                        pointer++;
                        var15 = pointer < inputLength ? input.codePointAt(pointer) : -1;
                     }

                     address[pieceIndex] = address[pieceIndex] * 256 + (ipv4Piece != null ? ipv4Piece : 0);
                     numbersSeen++;
                     if (numbersSeen == 2 || numbersSeen == 4) {
                        pieceIndex++;
                     }
                  }

                  if (numbersSeen != 4) {
                     throw new InvalidUrlException("IPv6 address with IPv4 address syntax: IPv4 address contains too few parts.");
                  }
                  break;
               }

               if (c == 58) {
                  pointer++;
                  c = pointer < inputLength ? input.codePointAt(pointer) : -1;
                  if (c == -1) {
                     throw new InvalidUrlException("IPv6 address unexpectedly ends.");
                  }
               } else if (c != -1) {
                  throw new InvalidUrlException("IPv6 address contains \"" + Character.toString(c) + "\", which is neither an ASCII hex digit nor a ':'.");
               }

               address[pieceIndex] = value;
               pieceIndex++;
            }
         }

         if (compress != null) {
            int swaps = pieceIndex - compress;

            for (int var12 = 7; var12 != 0 && swaps > 0; swaps--) {
               int tmp = address[var12];
               address[var12] = address[compress + swaps - 1];
               address[compress + swaps - 1] = tmp;
               var12--;
            }
         } else if (pieceIndex != 8) {
            throw new InvalidUrlException("An uncompressed IPv6 address contains fewer than 8 pieces.");
         }

         return new WhatWgUrlParser.Ipv6Address(address);
      }

      private static String serialize(int[] address) {
         StringBuilder output = new StringBuilder();
         int compress = longestSequenceOf0Pieces(address);
         boolean ignore0 = false;

         for (int pieceIndex = 0; pieceIndex <= 7; pieceIndex++) {
            if (!ignore0 || address[pieceIndex] != 0) {
               if (ignore0) {
                  ignore0 = false;
               }

               if (compress == pieceIndex) {
                  String separator = pieceIndex == 0 ? "::" : ":";
                  output.append(separator);
                  ignore0 = true;
               } else {
                  output.append(Integer.toHexString(address[pieceIndex]));
                  if (pieceIndex != 7) {
                     output.append(':');
                  }
               }
            }
         }

         return output.toString();
      }

      private static int longestSequenceOf0Pieces(int[] pieces) {
         int longestStart = -1;
         int longestLength = -1;
         int start = -1;

         for (int i = 0; i < pieces.length + 1; i++) {
            if (i < pieces.length && pieces[i] == 0) {
               if (start < 0) {
                  start = i;
               }
            } else if (start >= 0) {
               int length = i - start;
               if (length > longestLength) {
                  longestStart = start;
                  longestLength = length;
               }

               start = -1;
            }
         }

         return longestLength > 1 ? longestStart : -1;
      }

      @Override
      public boolean equals(@Nullable Object obj) {
         if (obj == this) {
            return true;
         } else {
            return obj instanceof WhatWgUrlParser.Ipv6Address other ? Arrays.equals(this.pieces, other.pieces) : false;
         }
      }

      @Override
      public int hashCode() {
         return Arrays.hashCode(this.pieces);
      }

      @Override
      public String toString() {
         return this.string;
      }
   }

   static final class OpaqueHost implements WhatWgUrlParser.Host {
      private final String host;

      private OpaqueHost(String host) {
         this.host = host;
      }

      public static WhatWgUrlParser.OpaqueHost parse(String input, WhatWgUrlParser p) {
         for (int i = 0; i < input.length(); i++) {
            int ch = input.codePointAt(i);
            if (WhatWgUrlParser.isForbiddenHost(ch)) {
               throw new InvalidUrlException("An opaque host contains a forbidden host code point.");
            }

            if (p.validate() && !WhatWgUrlParser.isUrlCodePoint(ch) && ch != 37) {
               p.validationError("Code point \"" + ch + "\" is not a URL unit.");
            }

            if (p.validate()
               && ch == 37
               && (
                  input.length() - i < 3
                     || !WhatWgUrlParser.isAsciiHexDigit(input.codePointAt(i + 1))
                     || !WhatWgUrlParser.isAsciiHexDigit(input.codePointAt(i + 2))
               )) {
               p.validationError("Code point \"" + ch + "\" is not a URL unit.");
            }
         }

         String encoded = p.percentEncode(input, WhatWgUrlParser::c0ControlPercentEncodeSet);
         return new WhatWgUrlParser.OpaqueHost(encoded);
      }

      @Override
      public boolean equals(@Nullable Object obj) {
         if (obj == this) {
            return true;
         } else {
            return obj instanceof WhatWgUrlParser.OpaqueHost other ? this.host.equals(other.host) : false;
         }
      }

      @Override
      public int hashCode() {
         return this.host.hashCode();
      }

      @Override
      public String toString() {
         return this.host;
      }
   }

   private static final class ParseIpv4NumberFailure implements WhatWgUrlParser.ParseIpv4NumberResult {
      public static final WhatWgUrlParser.ParseIpv4NumberFailure INSTANCE = new WhatWgUrlParser.ParseIpv4NumberFailure();
   }

   private sealed interface ParseIpv4NumberResult permits WhatWgUrlParser.ParseIpv4NumberFailure, WhatWgUrlParser.ParseIpv4NumberSuccess {
   }

   private record ParseIpv4NumberSuccess(int number, boolean validationError) implements WhatWgUrlParser.ParseIpv4NumberResult {
   }

   sealed interface Path permits WhatWgUrlParser.PathSegment, WhatWgUrlParser.PathSegments {
      void append(int codePoint);

      void append(String s);

      boolean isEmpty();

      void shorten(String scheme);

      boolean isOpaque();

      WhatWgUrlParser.Path clone();

      String name();
   }

   static final class PathSegment implements WhatWgUrlParser.Path {
      private @Nullable StringBuilder builder = null;
      @Nullable String segment;

      PathSegment(String segment) {
         this.segment = segment;
      }

      PathSegment(int codePoint) {
         this.append(codePoint);
      }

      public String segment() {
         String result = this.segment;
         if (result == null) {
            Assert.state(this.builder != null, "String nor StringBuilder available");
            result = this.builder.toString();
            this.segment = result;
         }

         return result;
      }

      @Override
      public void append(int codePoint) {
         this.segment = null;
         if (this.builder == null) {
            this.builder = new StringBuilder(2);
         }

         this.builder.appendCodePoint(codePoint);
      }

      @Override
      public void append(String s) {
         this.segment = null;
         if (this.builder == null) {
            this.builder = new StringBuilder(s);
         } else {
            this.builder.append(s);
         }
      }

      @Override
      public String name() {
         String name = this.segment();
         if (name.startsWith("/")) {
            name = name.substring(1);
         }

         return name;
      }

      @Override
      public boolean isEmpty() {
         if (this.segment != null) {
            return this.segment.isEmpty();
         }

         Assert.state(this.builder != null, "String nor StringBuilder available");
         return this.builder.isEmpty();
      }

      @Override
      public void shorten(String scheme) {
         throw new IllegalStateException("Opaque path not expected");
      }

      @Override
      public boolean isOpaque() {
         return true;
      }

      @Override
      public WhatWgUrlParser.Path clone() {
         return new WhatWgUrlParser.PathSegment(this.segment());
      }

      @Override
      public boolean equals(@Nullable Object o) {
         if (o == this) {
            return true;
         } else {
            return o instanceof WhatWgUrlParser.PathSegment other ? this.segment().equals(other.segment()) : false;
         }
      }

      @Override
      public int hashCode() {
         return this.segment().hashCode();
      }

      @Override
      public String toString() {
         return this.segment();
      }
   }

   static final class PathSegments implements WhatWgUrlParser.Path {
      private final List<WhatWgUrlParser.PathSegment> segments;

      public PathSegments() {
         this.segments = new ArrayList<>();
      }

      public PathSegments(List<WhatWgUrlParser.PathSegment> segments) {
         this.segments = new ArrayList<>(segments);
      }

      @Override
      public void append(int codePoint) {
         this.segments.add(new WhatWgUrlParser.PathSegment(codePoint));
      }

      @Override
      public void append(String segment) {
         this.segments.add(new WhatWgUrlParser.PathSegment(segment));
      }

      public int size() {
         return this.segments.size();
      }

      public String get(int i) {
         return this.segments.get(i).segment();
      }

      @Override
      public boolean isEmpty() {
         return this.segments.isEmpty();
      }

      @Override
      public void shorten(String scheme) {
         int size = this.size();
         if (!"file".equals(scheme) || size != 1 || !WhatWgUrlParser.isWindowsDriveLetter(this.get(0), true)) {
            if (!this.isEmpty()) {
               this.segments.remove(size - 1);
            }
         }
      }

      @Override
      public boolean isOpaque() {
         return false;
      }

      @Override
      public WhatWgUrlParser.Path clone() {
         return new WhatWgUrlParser.PathSegments(this.segments);
      }

      @Override
      public String name() {
         StringBuilder output = new StringBuilder();

         for (WhatWgUrlParser.PathSegment segment : this.segments) {
            output.append('/');
            output.append(segment.name());
         }

         return output.toString();
      }

      @Override
      public boolean equals(@Nullable Object o) {
         if (o == this) {
            return true;
         } else {
            return o instanceof WhatWgUrlParser.PathSegments other ? this.segments.equals(other.segments) : false;
         }
      }

      @Override
      public int hashCode() {
         return this.segments.hashCode();
      }

      @Override
      public String toString() {
         StringBuilder output = new StringBuilder();

         for (WhatWgUrlParser.PathSegment segment : this.segments) {
            output.append(segment);
         }

         return output.toString();
      }
   }

   sealed interface Port permits WhatWgUrlParser.StringPort, WhatWgUrlParser.IntPort {
   }

   private enum State {
      SCHEME_START {
         @Override
         public void handle(int c, WhatWgUrlParser.UrlRecord url, WhatWgUrlParser p) {
            if (WhatWgUrlParser.isAsciiAlpha(c)) {
               p.append(p.openCurlyBracketCount == 0 ? Character.toLowerCase((char)c) : c);
               p.setState(SCHEME);
            } else if (c == 123) {
               p.openCurlyBracketCount++;
               p.append(c);
               p.setState(SCHEME);
            } else if (p.stateOverride == null) {
               p.setState(NO_SCHEME);
               p.pointer--;
            } else {
               p.failure(null);
            }
         }
      },
      SCHEME {
         @Override
         public void handle(int c, WhatWgUrlParser.UrlRecord url, WhatWgUrlParser p) {
            if (!WhatWgUrlParser.isAsciiAlphaNumeric(c) && c != 43 && c != 45 && c != 46) {
               if (c != 58) {
                  if (p.processCurlyBrackets(c)) {
                     p.append(c);
                  } else if (p.stateOverride == null) {
                     p.emptyBuffer();
                     p.setState(NO_SCHEME);
                     p.pointer = -1;
                  } else {
                     p.failure(null);
                  }
               } else {
                  if (p.stateOverride != null) {
                     boolean urlSpecialScheme = url.isSpecial();
                     String bufferString = p.buffer.toString();
                     boolean bufferSpecialScheme = WhatWgUrlParser.isSpecialScheme(bufferString);
                     if (urlSpecialScheme && !bufferSpecialScheme) {
                        return;
                     }

                     if (!urlSpecialScheme && bufferSpecialScheme) {
                        return;
                     }

                     if ((url.includesCredentials() || url.port() != null) && "file".equals(bufferString)) {
                        return;
                     }

                     if ("file".equals(url.scheme()) && (url.host() == null || url.host() == WhatWgUrlParser.EmptyHost.INSTANCE)) {
                        return;
                     }
                  }

                  url.scheme = p.buffer.toString();
                  if (p.stateOverride != null
                     && url.port instanceof WhatWgUrlParser.IntPort intPort
                     && intPort.value() == WhatWgUrlParser.defaultPort(url.scheme)) {
                     url.port = null;
                     p.stopMainLoop = true;
                     return;
                  }

                  p.emptyBuffer();
                  if (url.scheme.equals("file")) {
                     if (p.validate() && (p.remaining(0) != 47 || p.remaining(1) != 47)) {
                        p.validationError("\"file\" scheme not followed by \"//\".");
                     }

                     p.setState(FILE);
                  } else if (url.isSpecial() && p.base != null && p.base.scheme().equals(url.scheme)) {
                     Assert.state(!p.base.path().isOpaque(), "Opaque path not expected");
                     p.setState(SPECIAL_RELATIVE_OR_AUTHORITY);
                  } else if (url.isSpecial()) {
                     p.setState(SPECIAL_AUTHORITY_SLASHES);
                  } else if (p.remaining(0) == 47) {
                     p.setState(PATH_OR_AUTHORITY);
                     p.pointer++;
                  } else {
                     url.path = new WhatWgUrlParser.PathSegment("");
                     p.setState(OPAQUE_PATH);
                  }
               }
            } else {
               p.append(p.openCurlyBracketCount == 0 ? Character.toLowerCase((char)c) : c);
            }
         }
      },
      NO_SCHEME {
         @Override
         public void handle(int c, WhatWgUrlParser.UrlRecord url, WhatWgUrlParser p) {
            if (p.base != null && (!p.base.path().isOpaque() || c == 35)) {
               if (p.base.path().isOpaque() && c == 35) {
                  url.scheme = p.base.scheme();
                  url.path = p.base.path();
                  url.query = p.base.query;
                  url.fragment = new StringBuilder();
                  p.setState(FRAGMENT);
               } else if (!"file".equals(p.base.scheme())) {
                  p.setState(RELATIVE);
                  p.pointer--;
               } else {
                  p.setState(FILE);
                  p.pointer--;
               }
            } else {
               p.failure(
                  "The input is missing a scheme, because it does not begin with an ASCII alpha \""
                     + (c != -1 ? Character.toString(c) : "")
                     + "\", and no base URL was provided."
               );
            }
         }
      },
      SPECIAL_RELATIVE_OR_AUTHORITY {
         @Override
         public void handle(int c, WhatWgUrlParser.UrlRecord url, WhatWgUrlParser p) {
            if (c == 47 && p.remaining(0) == 47) {
               p.setState(SPECIAL_AUTHORITY_IGNORE_SLASHES);
               p.pointer++;
            } else {
               if (p.validate()) {
                  p.validationError("The input’s scheme is not followed by \"//\".");
               }

               p.setState(RELATIVE);
               p.pointer--;
            }
         }
      },
      PATH_OR_AUTHORITY {
         @Override
         public void handle(int c, WhatWgUrlParser.UrlRecord url, WhatWgUrlParser p) {
            if (c == 47) {
               p.setState(AUTHORITY);
            } else {
               p.setState(PATH);
               p.pointer--;
            }
         }
      },
      RELATIVE {
         @Override
         public void handle(int c, WhatWgUrlParser.UrlRecord url, WhatWgUrlParser p) {
            Assert.state(p.base != null && !"file".equals(p.base.scheme()), "Base scheme not provided or supported");
            url.scheme = p.base.scheme;
            if (c == 47) {
               p.append('/');
               p.setState(RELATIVE_SLASH);
            } else if (url.isSpecial() && c == 92) {
               if (p.validate()) {
                  p.validationError("URL uses \\ instead of /.");
               }

               p.append('/');
               p.setState(RELATIVE_SLASH);
            } else {
               url.username = p.base.username != null ? new StringBuilder(p.base.username) : null;
               url.password = p.base.password != null ? new StringBuilder(p.base.password) : null;
               url.host = p.base.host();
               url.port = p.base.port();
               url.path = p.base.path().clone();
               url.query = p.base.query;
               if (c == 63) {
                  url.query = new StringBuilder();
                  p.setState(QUERY);
               } else if (c == 35) {
                  url.fragment = new StringBuilder();
                  p.setState(FRAGMENT);
               } else if (c != -1) {
                  url.query = null;
                  url.shortenPath();
                  p.setState(PATH);
                  p.pointer--;
               }
            }
         }
      },
      RELATIVE_SLASH {
         @Override
         public void handle(int c, WhatWgUrlParser.UrlRecord url, WhatWgUrlParser p) {
            if (url.isSpecial() && (c == 47 || c == 92)) {
               if (p.validate() && c == 92) {
                  p.validationError("URL uses \\ instead of /.");
               }

               p.setState(SPECIAL_AUTHORITY_IGNORE_SLASHES);
            } else if (c == 47) {
               p.emptyBuffer();
               p.setState(AUTHORITY);
            } else {
               Assert.state(p.base != null, "No base URL available");
               url.username = p.base.username != null ? new StringBuilder(p.base.username) : null;
               url.password = p.base.password != null ? new StringBuilder(p.base.password) : null;
               url.host = p.base.host();
               url.port = p.base.port();
               p.setState(PATH);
               p.pointer--;
            }
         }
      },
      SPECIAL_AUTHORITY_SLASHES {
         @Override
         public void handle(int c, WhatWgUrlParser.UrlRecord url, WhatWgUrlParser p) {
            if (c == 47 && p.remaining(0) == 47) {
               p.setState(SPECIAL_AUTHORITY_IGNORE_SLASHES);
               p.pointer++;
            } else {
               if (p.validate()) {
                  p.validationError("Scheme \"" + url.scheme + "\" not followed by \"//\".");
               }

               p.setState(SPECIAL_AUTHORITY_IGNORE_SLASHES);
               p.pointer--;
            }
         }
      },
      SPECIAL_AUTHORITY_IGNORE_SLASHES {
         @Override
         public void handle(int c, WhatWgUrlParser.UrlRecord url, WhatWgUrlParser p) {
            if (c != 47 && c != 92) {
               p.setState(AUTHORITY);
               p.pointer--;
            } else if (p.validate()) {
               p.validationError("Scheme \"" + url.scheme + "\" not followed by \"//\".");
            }
         }
      },
      AUTHORITY {
         @Override
         public void handle(int c, WhatWgUrlParser.UrlRecord url, WhatWgUrlParser p) {
            if (c == 64) {
               if (p.validate()) {
                  p.validationError("Invalid credentials");
               }

               if (p.atSignSeen) {
                  p.prepend("%40");
               }

               p.atSignSeen = true;
               int bufferLen = p.buffer.length();

               for (int i = 0; i < bufferLen; i++) {
                  int codePoint = p.buffer.codePointAt(i);
                  if (codePoint == 58 && !p.passwordTokenSeen) {
                     p.passwordTokenSeen = true;
                  } else {
                     String encodedCodePoints = p.percentEncode(codePoint, WhatWgUrlParser::userinfoPercentEncodeSet);
                     if (p.passwordTokenSeen) {
                        if (encodedCodePoints != null) {
                           url.appendToPassword(encodedCodePoints);
                        } else {
                           url.appendToPassword(codePoint);
                        }
                     } else if (encodedCodePoints != null) {
                        url.appendToUsername(encodedCodePoints);
                     } else {
                        url.appendToUsername(codePoint);
                     }
                  }
               }

               p.emptyBuffer();
            } else if (c == -1 || c == 47 || c == 63 || c == 35 || url.isSpecial() && c == 92) {
               if (p.atSignSeen && p.buffer.isEmpty()) {
                  p.failure("Missing host.");
               }

               p.pointer = p.pointer - (p.buffer.length() + 1);
               p.emptyBuffer();
               p.setState(HOST);
            } else {
               p.append(c);
            }
         }
      },
      HOST {
         @Override
         public void handle(int c, WhatWgUrlParser.UrlRecord url, WhatWgUrlParser p) {
            if (p.stateOverride != null && "file".equals(url.scheme())) {
               p.pointer--;
               p.setState(FILE_HOST);
            } else if (c == 58 && !p.insideBrackets) {
               if (p.buffer.isEmpty()) {
                  p.failure("Missing host.");
               }

               if (p.stateOverride == HOST) {
                  p.stopMainLoop = true;
                  return;
               }

               url.host = WhatWgUrlParser.Host.parse(p.buffer.toString(), !url.isSpecial(), p);
               p.emptyBuffer();
               p.setState(PORT);
            } else if (c == -1 || c == 47 || c == 63 || c == 35 || url.isSpecial() && c == 92) {
               p.pointer--;
               if (url.isSpecial() && p.buffer.isEmpty()) {
                  p.failure("The input has a special scheme, but does not contain a host.");
               } else if (p.stateOverride != null && p.buffer.isEmpty() && (url.includesCredentials() || url.port() != null)) {
                  p.stopMainLoop = true;
                  return;
               }

               if (!p.buffer.isEmpty()) {
                  url.host = WhatWgUrlParser.Host.parse(p.buffer.toString(), !url.isSpecial(), p);
               } else {
                  url.host = WhatWgUrlParser.EmptyHost.INSTANCE;
               }

               p.emptyBuffer();
               p.setState(PATH_START);
               if (p.stateOverride != null) {
                  p.stopMainLoop = true;
               }
            } else {
               if (c == 91) {
                  p.insideBrackets = true;
               } else if (c == 93) {
                  p.insideBrackets = false;
               }

               p.append(c);
            }
         }
      },
      PORT {
         @Override
         public void handle(int c, WhatWgUrlParser.UrlRecord url, WhatWgUrlParser p) {
            if (WhatWgUrlParser.isAsciiDigit(c)) {
               p.append(c);
            } else if (c != -1 && c != 47 && c != 63 && c != 35 && (!url.isSpecial() || c != 92) && p.stateOverride == null) {
               if (p.processCurlyBrackets(c)) {
                  p.append(c);
               } else {
                  p.failure("Invalid port: \"" + Character.toString(c) + "\"");
               }
            } else {
               if (!p.buffer.isEmpty()) {
                  if (WhatWgUrlParser.containsOnlyAsciiDigits(p.buffer)) {
                     try {
                        int port = Integer.parseInt(p.buffer, 0, p.buffer.length(), 10);
                        if (port > 65535) {
                           p.failure("Port \"" + port + "\" is out of range");
                        }

                        int defaultPort = WhatWgUrlParser.defaultPort(url.scheme);
                        if (defaultPort != -1 && port == defaultPort) {
                           url.port = null;
                        } else {
                           url.port = new WhatWgUrlParser.IntPort(port);
                        }
                     } catch (NumberFormatException ex) {
                        p.failure(ex.getMessage());
                     }
                  } else {
                     url.port = new WhatWgUrlParser.StringPort(p.buffer.toString());
                  }

                  p.emptyBuffer();
               }

               if (p.stateOverride != null) {
                  p.stopMainLoop = true;
                  return;
               }

               p.setState(PATH_START);
               p.pointer--;
            }
         }
      },
      FILE {
         @Override
         public void handle(int c, WhatWgUrlParser.UrlRecord url, WhatWgUrlParser p) {
            url.scheme = "file";
            url.host = WhatWgUrlParser.EmptyHost.INSTANCE;
            if (c != 47 && c != 92) {
               if (p.base != null && p.base.scheme().equals("file")) {
                  url.host = p.base.host;
                  url.path = p.base.path().clone();
                  url.query = p.base.query;
                  if (c == 63) {
                     url.query = new StringBuilder();
                     p.setState(QUERY);
                  } else if (c == 35) {
                     url.fragment = new StringBuilder();
                     p.setState(FRAGMENT);
                  } else if (c != -1) {
                     url.query = null;
                     String substring = p.input.substring(p.pointer);
                     if (!WhatWgUrlParser.startsWithWindowsDriveLetter(substring)) {
                        url.shortenPath();
                     } else {
                        if (p.validate()) {
                           p.validationError(
                              "The input is a relative-URL string that starts with a Windows drive letter and the base URL’s scheme is \"file\"."
                           );
                        }

                        url.path = new WhatWgUrlParser.PathSegments();
                     }

                     p.setState(PATH);
                     p.pointer--;
                  }
               } else {
                  p.setState(PATH);
                  p.pointer--;
               }
            } else {
               if (p.validate() && c == 92) {
                  p.validationError("URL uses \\ instead of /.");
               }

               p.setState(FILE_SLASH);
            }
         }
      },
      FILE_SLASH {
         @Override
         public void handle(int c, WhatWgUrlParser.UrlRecord url, WhatWgUrlParser p) {
            if (c != 47 && c != 92) {
               if (p.base != null && p.base.scheme.equals("file")) {
                  url.host = p.base.host;
                  String substring = p.input.substring(p.pointer);
                  if (!WhatWgUrlParser.startsWithWindowsDriveLetter(substring)
                     && p.base.path instanceof WhatWgUrlParser.PathSegments basePath
                     && !basePath.isEmpty()
                     && WhatWgUrlParser.isWindowsDriveLetter(basePath.get(0), true)) {
                     url.path.append(basePath.get(0));
                  }
               }

               p.setState(PATH);
               p.pointer--;
            } else {
               if (p.validate() && c == 92) {
                  p.validationError("URL uses \\ instead of /.");
               }

               p.setState(FILE_HOST);
            }
         }
      },
      FILE_HOST {
         @Override
         public void handle(int c, WhatWgUrlParser.UrlRecord url, WhatWgUrlParser p) {
            if (c != -1 && c != 47 && c != 92 && c != 63 && c != 35) {
               p.append(c);
            } else {
               p.pointer--;
               if (p.stateOverride == null && WhatWgUrlParser.isWindowsDriveLetter(p.buffer, false)) {
                  p.validationError("A file: URL’s host is a Windows drive letter.");
                  p.setState(PATH);
               } else if (p.buffer.isEmpty()) {
                  url.host = WhatWgUrlParser.EmptyHost.INSTANCE;
                  if (p.stateOverride != null) {
                     p.stopMainLoop = true;
                     return;
                  }

                  p.setState(PATH_START);
               } else {
                  WhatWgUrlParser.Host host = WhatWgUrlParser.Host.parse(p.buffer.toString(), !url.isSpecial(), p);
                  if (host instanceof WhatWgUrlParser.Domain domain && domain.domain().equals("localhost")) {
                     host = WhatWgUrlParser.EmptyHost.INSTANCE;
                  }

                  url.host = host;
                  if (p.stateOverride != null) {
                     p.stopMainLoop = true;
                     return;
                  }

                  p.emptyBuffer();
                  p.setState(PATH_START);
               }
            }
         }
      },
      PATH_START {
         @Override
         public void handle(int c, WhatWgUrlParser.UrlRecord url, WhatWgUrlParser p) {
            if (url.isSpecial()) {
               if (p.validate() && c == 92) {
                  p.validationError("URL uses \"\\\" instead of \"/\"");
               }

               p.setState(PATH);
               if (c != 47 && c != 92) {
                  p.pointer--;
               } else {
                  p.append('/');
               }
            } else if (p.stateOverride == null && c == 63) {
               url.query = new StringBuilder();
               p.setState(QUERY);
            } else if (p.stateOverride == null && c == 35) {
               url.fragment = new StringBuilder();
               p.setState(FRAGMENT);
            } else if (c != -1) {
               p.setState(PATH);
               if (c != 47) {
                  p.pointer--;
               } else {
                  p.append('/');
               }
            } else if (p.stateOverride != null && url.host() == null) {
               url.path().append("");
            }
         }
      },
      PATH {
         @Override
         public void handle(int c, WhatWgUrlParser.UrlRecord url, WhatWgUrlParser p) {
            if (c != -1 && c != 47 && (!url.isSpecial() || c != 92) && (p.stateOverride != null || c != 63 && c != 35)) {
               if (p.validate()) {
                  if (!WhatWgUrlParser.isUrlCodePoint(c) && c != 37) {
                     p.validationError("Invalid URL Unit: \"" + (char)c + "\"");
                  } else if (c == 37
                     && (
                        p.pointer >= p.input.length() - 2
                           || !WhatWgUrlParser.isAsciiHexDigit(p.input.codePointAt(p.pointer + 1))
                           || !WhatWgUrlParser.isAsciiHexDigit(p.input.codePointAt(p.pointer + 2))
                     )) {
                     p.validationError("Invalid URL Unit: \"" + (char)c + "\"");
                  }
               }

               String encoded = p.percentEncode(c, WhatWgUrlParser::pathPercentEncodeSet);
               if (encoded != null) {
                  p.append(encoded);
               } else {
                  p.append(c);
               }
            } else {
               if (p.validate() && url.isSpecial() && c == 92) {
                  p.validationError("URL uses \"\\\" instead of \"/\"");
               }

               if (WhatWgUrlParser.isDoubleDotPathSegment(p.buffer)) {
                  url.shortenPath();
                  if (c != 47 && (!url.isSpecial() || c != 92)) {
                     url.path.append("");
                  }
               } else {
                  boolean singlePathSegment = WhatWgUrlParser.isSingleDotPathSegment(p.buffer);
                  if (!singlePathSegment || c == 47 || url.isSpecial() && c == 92) {
                     if (!singlePathSegment) {
                        if ("file".equals(url.scheme) && url.path.isEmpty() && WhatWgUrlParser.isWindowsDriveLetter(p.buffer, false)) {
                           p.buffer.setCharAt(1, ':');
                        }

                        url.path.append(p.buffer.toString());
                     }
                  } else {
                     url.path.append("");
                  }
               }

               p.emptyBuffer();
               if (c == 47 || url.isSpecial() && c == 92) {
                  p.append('/');
               }

               if (c == 63) {
                  url.query = new StringBuilder();
                  p.setState(QUERY);
               }

               if (c == 35) {
                  url.fragment = new StringBuilder();
                  p.setState(FRAGMENT);
               }
            }
         }
      },
      OPAQUE_PATH {
         @Override
         public void handle(int c, WhatWgUrlParser.UrlRecord url, WhatWgUrlParser p) {
            if (c == 63) {
               url.query = new StringBuilder();
               p.setState(QUERY);
            } else if (c == 35) {
               url.fragment = new StringBuilder();
               p.setState(FRAGMENT);
            } else {
               if (p.validate()) {
                  if (c != -1 && !WhatWgUrlParser.isUrlCodePoint(c) && c != 37) {
                     p.validationError("Invalid URL Unit: \"" + (char)c + "\"");
                  } else if (c == 37
                     && (
                        p.pointer >= p.input.length() - 2
                           || !WhatWgUrlParser.isAsciiHexDigit(p.input.codePointAt(p.pointer + 1))
                           || !WhatWgUrlParser.isAsciiHexDigit(p.input.codePointAt(p.pointer + 2))
                     )) {
                     p.validationError("Invalid URL Unit: \"" + (char)c + "\"");
                  }
               }

               if (c != -1) {
                  String encoded = p.percentEncode(c, WhatWgUrlParser::c0ControlPercentEncodeSet);
                  if (encoded != null) {
                     url.path.append(encoded);
                  } else {
                     url.path.append(c);
                  }
               }
            }
         }
      },
      QUERY {
         @Override
         public void handle(int c, WhatWgUrlParser.UrlRecord url, WhatWgUrlParser p) {
            if (p.encoding != null && !StandardCharsets.UTF_8.equals(p.encoding) && (!url.isSpecial() || "ws".equals(url.scheme) || "wss".equals(url.scheme))) {
               p.encoding = StandardCharsets.UTF_8;
            }

            if ((p.stateOverride != null || c != 35) && c != -1) {
               if (p.validate()) {
                  if (!WhatWgUrlParser.isUrlCodePoint(c) && c != 37) {
                     p.validationError("Invalid URL Unit: \"" + (char)c + "\"");
                  } else if (c == 37
                     && (
                        p.pointer >= p.input.length() - 2
                           || !WhatWgUrlParser.isAsciiHexDigit(p.input.codePointAt(p.pointer + 1))
                           || !WhatWgUrlParser.isAsciiHexDigit(p.input.codePointAt(p.pointer + 2))
                     )) {
                     p.validationError("Invalid URL Unit: \"" + (char)c + "\"");
                  }
               }

               p.append(c);
            } else {
               IntPredicate queryPercentEncodeSet = url.isSpecial() ? WhatWgUrlParser::specialQueryPercentEncodeSet : WhatWgUrlParser::queryPercentEncodeSet;
               String encoded = p.percentEncode(p.buffer.toString(), queryPercentEncodeSet);
               Assert.state(url.query != null, "Url's query should not be null");
               url.query.append(encoded);
               p.emptyBuffer();
               if (c == 35) {
                  url.fragment = new StringBuilder();
                  p.setState(FRAGMENT);
               }
            }
         }
      },
      FRAGMENT {
         @Override
         public void handle(int c, WhatWgUrlParser.UrlRecord url, WhatWgUrlParser p) {
            if (c != -1) {
               if (p.validate()) {
                  if (!WhatWgUrlParser.isUrlCodePoint(c) && c != 37) {
                     p.validationError("Invalid URL Unit: \"" + (char)c + "\"");
                  } else if (c == 37
                     && (
                        p.pointer >= p.input.length() - 2
                           || !WhatWgUrlParser.isAsciiHexDigit(p.input.codePointAt(p.pointer + 1))
                           || !WhatWgUrlParser.isAsciiHexDigit(p.input.codePointAt(p.pointer + 2))
                     )) {
                     p.validationError("Invalid URL Unit: \"" + (char)c + "\"");
                  }
               }

               String encoded = p.percentEncode(c, WhatWgUrlParser::fragmentPercentEncodeSet);
               Assert.state(url.fragment != null, "Url's fragment should not be null");
               if (encoded != null) {
                  url.fragment.append(encoded);
               } else {
                  url.fragment.appendCodePoint(c);
               }
            }
         }
      };

      public abstract void handle(int c, WhatWgUrlParser.UrlRecord url, WhatWgUrlParser p);
   }

   static final class StringPort implements WhatWgUrlParser.Port {
      private final String port;

      public StringPort(String port) {
         this.port = port;
      }

      public String value() {
         return this.port;
      }

      @Override
      public String toString() {
         return this.port;
      }
   }

   static final class UrlRecord {
      private String scheme = "";
      private @Nullable StringBuilder username = null;
      private @Nullable StringBuilder password = null;
      private WhatWgUrlParser.@Nullable Host host = null;
      private WhatWgUrlParser.@Nullable Port port = null;
      private WhatWgUrlParser.Path path = new WhatWgUrlParser.PathSegments();
      private @Nullable StringBuilder query = null;
      private @Nullable StringBuilder fragment = null;

      public UrlRecord() {
      }

      public boolean isSpecial() {
         return WhatWgUrlParser.isSpecialScheme(this.scheme);
      }

      public boolean includesCredentials() {
         return this.username != null && !this.username.isEmpty() || this.password != null && !this.password.isEmpty();
      }

      public boolean hasOpaquePath() {
         return this.path().isOpaque();
      }

      public String scheme() {
         return this.scheme;
      }

      public String protocol() {
         return this.scheme() + ":";
      }

      public String username() {
         return this.username != null ? this.username.toString() : "";
      }

      void appendToUsername(int codePoint) {
         if (this.username == null) {
            this.username = new StringBuilder(2);
         }

         this.username.appendCodePoint(codePoint);
      }

      public void appendToUsername(String s) {
         if (this.username == null) {
            this.username = new StringBuilder(s);
         } else {
            this.username.append(s);
         }
      }

      public String password() {
         return this.password != null ? this.password.toString() : "";
      }

      void appendToPassword(int codePoint) {
         if (this.password == null) {
            this.password = new StringBuilder(2);
         }

         this.password.appendCodePoint(codePoint);
      }

      void appendToPassword(String s) {
         if (this.password == null) {
            this.password = new StringBuilder(s);
         } else {
            this.password.append(s);
         }
      }

      public @Nullable String userInfo() {
         if (!this.includesCredentials()) {
            return null;
         }

         StringBuilder userInfo = new StringBuilder(this.username());
         if (!this.password().isEmpty()) {
            userInfo.append(':');
            userInfo.append(this.password());
         }

         return userInfo.toString();
      }

      public WhatWgUrlParser.@Nullable Host host() {
         return this.host;
      }

      public String hostString() {
         if (this.host() == null) {
            return "";
         }

         StringBuilder builder = new StringBuilder(this.hostname());
         WhatWgUrlParser.Port port = this.port();
         if (port != null) {
            builder.append(':');
            builder.append(port);
         }

         return builder.toString();
      }

      public String hostname() {
         WhatWgUrlParser.Host host = this.host();
         return host != null ? host.toString() : "";
      }

      public WhatWgUrlParser.@Nullable Port port() {
         return this.port;
      }

      public String portString() {
         return this.port() != null ? this.port().toString() : "";
      }

      public WhatWgUrlParser.Path path() {
         return this.path;
      }

      public String pathname() {
         return this.path().name();
      }

      public void shortenPath() {
         this.path.shorten(this.scheme);
      }

      public @Nullable String query() {
         return this.query != null ? this.query.toString() : null;
      }

      public String search() {
         String query = this.query();
         return query == null ? "" : "?" + query;
      }

      public @Nullable String fragment() {
         return this.fragment != null ? this.fragment.toString() : null;
      }

      public String hash() {
         String fragment = this.fragment();
         return fragment != null && !fragment.isEmpty() ? "#" + fragment : "";
      }

      public String href() {
         StringBuilder output = new StringBuilder(this.scheme());
         output.append(':');
         WhatWgUrlParser.Host host = this.host();
         if (host != null) {
            output.append("//");
            if (this.includesCredentials()) {
               output.append(this.username());
               String password = this.password();
               if (!password.isEmpty()) {
                  output.append(':');
                  output.append(password);
               }

               output.append('@');
            }

            output.append(this.hostname());
            WhatWgUrlParser.Port port = this.port();
            if (port != null) {
               output.append(':');
               output.append(this.port());
            }
         } else if (!this.hasOpaquePath()
            && this.path() instanceof WhatWgUrlParser.PathSegments pathSegments
            && pathSegments.size() > 1
            && pathSegments.get(0).isEmpty()) {
            output.append("/.");
         }

         output.append(this.pathname());
         String query = this.query();
         if (query != null) {
            output.append('?');
            output.append(query);
         }

         String fragment = this.fragment();
         if (fragment != null) {
            output.append('#');
            output.append(fragment);
         }

         return output.toString();
      }

      @Override
      public boolean equals(@Nullable Object obj) {
         if (obj == this) {
            return true;
         } else if (obj != null && this.getClass() == obj.getClass()) {
            WhatWgUrlParser.UrlRecord that = (WhatWgUrlParser.UrlRecord)obj;
            return Objects.equals(this.scheme(), that.scheme())
               && Objects.equals(this.username(), that.username())
               && Objects.equals(this.password(), that.password())
               && Objects.equals(this.host(), that.host())
               && Objects.equals(this.port(), that.port())
               && Objects.equals(this.path(), that.path())
               && Objects.equals(this.query(), that.query())
               && Objects.equals(this.fragment(), that.fragment());
         } else {
            return false;
         }
      }

      @Override
      public int hashCode() {
         return Objects.hash(this.scheme, this.username, this.password, this.host, this.port, this.path, this.query, this.fragment);
      }

      @Override
      public String toString() {
         return "UrlRecord[scheme="
            + this.scheme
            + ", username="
            + this.username
            + ", password="
            + this.password
            + ", host="
            + this.host
            + ", port="
            + this.port
            + ", path="
            + this.path
            + ", query="
            + this.query
            + ", fragment="
            + this.fragment
            + "]";
      }
   }
}
