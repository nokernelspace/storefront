package org.springframework.http;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Base64.Decoder;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StreamUtils;

public final class ContentDisposition {
   private static final Pattern BASE64_ENCODED_PATTERN = Pattern.compile("=\\?([0-9a-zA-Z-_]+)\\?B\\?([+/0-9a-zA-Z]+=*)\\?=");
   private static final Pattern QUOTED_PRINTABLE_ENCODED_PATTERN = Pattern.compile("=\\?([0-9a-zA-Z-_]+)\\?Q\\?([!->@-~]+)\\?=");
   private static final String INVALID_HEADER_FIELD_PARAMETER_FORMAT = "Invalid header field parameter format (as defined in RFC 5987)";
   private static final HexFormat HEX_FORMAT = HexFormat.of().withUpperCase();
   private final @Nullable String type;
   private final @Nullable String name;
   private final @Nullable String filename;
   private final @Nullable Charset charset;

   private ContentDisposition(@Nullable String type, @Nullable String name, @Nullable String filename, @Nullable Charset charset) {
      this.type = type;
      this.name = name;
      this.filename = filename;
      this.charset = charset;
   }

   public boolean isAttachment() {
      return this.isDispositionType("attachment");
   }

   public boolean isFormData() {
      return this.isDispositionType("form-data");
   }

   public boolean isInline() {
      return this.isDispositionType("inline");
   }

   private boolean isDispositionType(String type) {
      return this.type != null && this.type.equalsIgnoreCase(type);
   }

   public @Nullable String getType() {
      return this.type;
   }

   public @Nullable String getName() {
      return this.name;
   }

   public @Nullable String getFilename() {
      return this.filename;
   }

   public @Nullable Charset getCharset() {
      return this.charset;
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof ContentDisposition that
            && ObjectUtils.nullSafeEquals(this.type, that.type)
            && ObjectUtils.nullSafeEquals(this.name, that.name)
            && ObjectUtils.nullSafeEquals(this.filename, that.filename)
            && ObjectUtils.nullSafeEquals(this.charset, that.charset);
   }

   @Override
   public int hashCode() {
      return ObjectUtils.nullSafeHash(new Object[]{this.type, this.name, this.filename, this.charset});
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder();
      if (this.type != null) {
         sb.append(this.type);
      }

      if (this.name != null) {
         sb.append("; name=\"");
         appendName(sb, this.name).append('"');
      }

      if (this.filename != null) {
         if (this.charset != null && !StandardCharsets.US_ASCII.equals(this.charset)) {
            sb.append("; filename=\"")
               .append(transliterateToAscii(appendName(new StringBuilder(), this.filename).toString()))
               .append("\"; filename*=")
               .append(encodeRfc5987Filename(this.filename, this.charset));
         } else {
            sb.append("; filename=\"");
            appendName(sb, this.filename).append('"');
         }
      }

      return sb.toString();
   }

   public static ContentDisposition.Builder attachment() {
      return builder("attachment");
   }

   public static ContentDisposition.Builder formData() {
      return builder("form-data");
   }

   public static ContentDisposition.Builder inline() {
      return builder("inline");
   }

   public static ContentDisposition.Builder builder(String type) {
      return new ContentDisposition.BuilderImpl(type);
   }

   public static ContentDisposition empty() {
      return new ContentDisposition("", null, null, null);
   }

   public static ContentDisposition parse(String contentDisposition) {
      List<String> parts = tokenize(contentDisposition);
      String type = parts.get(0);
      String name = null;
      String filename = null;
      Charset charset = null;

      for (int i = 1; i < parts.size(); i++) {
         String part = parts.get(i);
         int eqIndex = part.indexOf(61);
         if (eqIndex == -1) {
            throw new IllegalArgumentException("Invalid content disposition format");
         }

         String attribute = part.substring(0, eqIndex).toLowerCase(Locale.ROOT);
         String value = part.startsWith("\"", eqIndex + 1) && part.endsWith("\"")
            ? part.substring(eqIndex + 2, part.length() - 1)
            : part.substring(eqIndex + 1);
         if (attribute.equals("name")) {
            name = value.indexOf(92) != -1 ? decodeQuotedPairs(value) : value;
         } else if (attribute.equals("filename*")) {
            int idx1 = value.indexOf(39);
            int idx2 = value.indexOf(39, idx1 + 1);
            if (idx1 != -1 && idx2 != -1) {
               charset = Charset.forName(value.substring(0, idx1).trim());
               Assert.isTrue(StandardCharsets.UTF_8.equals(charset) || StandardCharsets.ISO_8859_1.equals(charset), "Charset must be UTF-8 or ISO-8859-1");
               filename = decodeRfc5987Filename(value.substring(idx2 + 1), charset);
            } else {
               filename = decodeRfc5987Filename(value, StandardCharsets.US_ASCII);
            }
         } else if (attribute.equals("filename") && filename == null) {
            if (!value.startsWith("=?")) {
               if (value.indexOf(92) != -1) {
                  filename = decodeQuotedPairs(value);
               } else {
                  filename = value;
               }
            } else {
               Matcher matcher = BASE64_ENCODED_PATTERN.matcher(value);
               if (matcher.find()) {
                  Decoder decoder = Base64.getDecoder();
                  StringBuilder builder = new StringBuilder();

                  do {
                     charset = Charset.forName(matcher.group(1));
                     byte[] decoded = decoder.decode(matcher.group(2));
                     builder.append(new String(decoded, charset));
                  } while (matcher.find());

                  filename = builder.toString();
               } else {
                  matcher = QUOTED_PRINTABLE_ENCODED_PATTERN.matcher(value);
                  if (!matcher.find()) {
                     filename = value;
                  } else {
                     StringBuilder builder = new StringBuilder();

                     do {
                        charset = Charset.forName(matcher.group(1));
                        String decoded = decodeQuotedPrintableFilename(matcher.group(2), charset);
                        builder.append(decoded);
                     } while (matcher.find());

                     filename = builder.toString();
                  }
               }
            }
         }
      }

      return new ContentDisposition(type, name, filename, charset);
   }

   private static List<String> tokenize(String headerValue) {
      int index = headerValue.indexOf(59);
      String type = (index >= 0 ? headerValue.substring(0, index) : headerValue).trim();
      if (type.isEmpty()) {
         throw new IllegalArgumentException("Content-Disposition header must not be empty");
      }

      List<String> parts = new ArrayList<>();
      parts.add(type);
      if (index >= 0) {
         do {
            int nextIndex = index + 1;
            boolean quoted = false;
            boolean escaped = false;

            while (nextIndex < headerValue.length()) {
               char ch = headerValue.charAt(nextIndex);
               if (ch == ';') {
                  if (!quoted) {
                     break;
                  }
               } else if (!escaped && ch == '"') {
                  quoted = !quoted;
               }

               escaped = !escaped && ch == '\\';
               nextIndex++;
            }

            String part = headerValue.substring(index + 1, nextIndex).trim();
            if (!part.isEmpty()) {
               parts.add(part);
            }

            index = nextIndex;
         } while (index < headerValue.length());
      }

      return parts;
   }

   private static String decodeRfc5987Filename(String filename, Charset charset) {
      Assert.notNull(filename, "'filename' must not be null");
      Assert.notNull(charset, "'charset' must not be null");
      byte[] value = filename.getBytes(charset);
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      int index = 0;

      while (index < value.length) {
         byte b = value[index];
         if (!isRFC5987AttrChar(b)) {
            if (b != 37 || index >= value.length - 2) {
               throw new IllegalArgumentException("Invalid header field parameter format (as defined in RFC 5987)");
            }

            try {
               int high = HexFormat.fromHexDigit(value[index + 1]);
               int low = HexFormat.fromHexDigit(value[index + 2]);
               baos.write(high << 4 | low);
            } catch (NumberFormatException ex) {
               throw new IllegalArgumentException("Invalid header field parameter format (as defined in RFC 5987)", ex);
            }

            index += 3;
         } else {
            baos.write((char)b);
            index++;
         }
      }

      return StreamUtils.copyToString(baos, charset);
   }

   private static boolean isRFC5987AttrChar(byte c) {
      return c >= 48 && c <= 57
         || c >= 97 && c <= 122
         || c >= 65 && c <= 90
         || c == 33
         || c == 35
         || c == 36
         || c == 38
         || c == 43
         || c == 45
         || c == 46
         || c == 94
         || c == 95
         || c == 96
         || c == 124
         || c == 126;
   }

   private static String decodeQuotedPrintableFilename(String filename, Charset charset) {
      Assert.notNull(filename, "'filename' must not be null");
      Assert.notNull(charset, "'charset' must not be null");
      byte[] value = filename.getBytes(StandardCharsets.US_ASCII);
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      int index = 0;

      while (index < value.length) {
         byte b = value[index];
         if (b == 95) {
            baos.write(32);
            index++;
         } else if (b == 61 && index < value.length - 2) {
            int i1 = HexFormat.fromHexDigit(value[index + 1]);
            int i2 = HexFormat.fromHexDigit(value[index + 2]);
            baos.write(i1 << 4 | i2);
            index += 3;
         } else {
            baos.write(b);
            index++;
         }
      }

      return StreamUtils.copyToString(baos, charset);
   }

   private static String transliterateToAscii(String input) {
      StringBuilder sb = new StringBuilder(input.length() + 16);
      int i = 0;

      while (i < input.length()) {
         int codePoint = input.codePointAt(i);
         i += Character.charCount(codePoint);
         if (codePoint <= 127) {
            sb.append((char)codePoint);
         } else {
            switch (codePoint) {
               case 196:
                  sb.append("Ae");
                  break;
               case 198:
                  sb.append("AE");
                  break;
               case 214:
                  sb.append("Oe");
                  break;
               case 220:
                  sb.append("Ue");
                  break;
               case 223:
                  sb.append("ss");
                  break;
               case 228:
                  sb.append("ae");
                  break;
               case 230:
                  sb.append("ae");
                  break;
               case 246:
                  sb.append("oe");
                  break;
               case 252:
                  sb.append("ue");
                  break;
               case 338:
                  sb.append("OE");
                  break;
               case 339:
                  sb.append("oe");
                  break;
               default:
                  String cpStr = new String(Character.toChars(codePoint));
                  String normalized = Normalizer.normalize(cpStr, Form.NFD);
                  int j = 0;

                  while (j < normalized.length()) {
                     int ncp = normalized.codePointAt(j);
                     j += Character.charCount(ncp);
                     if (ncp <= 127) {
                        sb.append((char)ncp);
                     } else {
                        int type = Character.getType(ncp);
                        if (type != 6 && type != 8 && type != 7) {
                           sb.append('_');
                        }
                     }
                  }
            }
         }
      }

      return sb.toString();
   }

   private static StringBuilder appendName(StringBuilder buffer, String name) {
      for (int i = 0; i < name.length(); i++) {
         char c = name.charAt(i);
         if (c > 31 && c != 127) {
            if (c == '"' || c == '\\') {
               buffer.append('\\');
            }

            buffer.append(c);
         }
      }

      return buffer;
   }

   private static String decodeQuotedPairs(String filename) {
      StringBuilder sb = new StringBuilder();
      int length = filename.length();

      for (int i = 0; i < length; i++) {
         char c = filename.charAt(i);
         if (filename.charAt(i) == '\\' && i + 1 < length) {
            char next = filename.charAt(++i);
            if (next != '"' && next != '\\') {
               sb.append(c);
            }

            sb.append(next);
         } else {
            sb.append(c);
         }
      }

      return sb.toString();
   }

   private static String encodeRfc5987Filename(String input, Charset charset) {
      Assert.notNull(input, "'input' must not be null");
      Assert.notNull(charset, "'charset' must not be null");
      Assert.isTrue(!StandardCharsets.US_ASCII.equals(charset), "ASCII does not require encoding");
      Assert.isTrue(StandardCharsets.UTF_8.equals(charset) || StandardCharsets.ISO_8859_1.equals(charset), "Only UTF-8 and ISO-8859-1 are supported");
      byte[] source = input.getBytes(charset);
      StringBuilder sb = new StringBuilder(source.length << 1);
      sb.append(charset.name());
      sb.append("''");

      for (byte b : source) {
         if (isRFC5987AttrChar(b)) {
            sb.append((char)b);
         } else {
            sb.append('%');
            HEX_FORMAT.toHexDigits(sb, b);
         }
      }

      return sb.toString();
   }

   public interface Builder {
      ContentDisposition.Builder name(@Nullable String name);

      ContentDisposition.Builder filename(@Nullable String filename);

      ContentDisposition.Builder filename(@Nullable String filename, @Nullable Charset charset);

      ContentDisposition build();
   }

   private static class BuilderImpl implements ContentDisposition.Builder {
      private final String type;
      private @Nullable String name;
      private @Nullable String filename;
      private @Nullable Charset charset;

      public BuilderImpl(String type) {
         Assert.hasText(type, "'type' must not be not empty");
         this.type = type;
      }

      @Override
      public ContentDisposition.Builder name(@Nullable String name) {
         this.name = name;
         return this;
      }

      @Override
      public ContentDisposition.Builder filename(@Nullable String filename) {
         this.filename = filename;
         return this;
      }

      @Override
      public ContentDisposition.Builder filename(@Nullable String filename, @Nullable Charset charset) {
         this.filename = filename;
         this.charset = charset;
         return this;
      }

      @Override
      public ContentDisposition build() {
         return new ContentDisposition(this.type, this.name, this.filename, this.charset);
      }
   }
}
