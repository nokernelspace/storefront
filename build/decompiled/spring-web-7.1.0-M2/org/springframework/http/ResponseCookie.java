package org.springframework.http;

import java.time.Duration;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public final class ResponseCookie extends HttpCookie {
   private final Duration maxAge;
   private final @Nullable String domain;
   private final @Nullable String path;
   private final boolean secure;
   private final boolean httpOnly;
   private final boolean partitioned;
   private final @Nullable String sameSite;

   private ResponseCookie(
      String name,
      @Nullable String value,
      Duration maxAge,
      @Nullable String domain,
      @Nullable String path,
      boolean secure,
      boolean httpOnly,
      boolean partitioned,
      @Nullable String sameSite
   ) {
      super(name, value);
      Assert.notNull(maxAge, "Max age must not be null");
      this.maxAge = maxAge;
      this.domain = domain;
      this.path = path;
      this.secure = secure;
      this.httpOnly = httpOnly;
      this.partitioned = partitioned;
      this.sameSite = sameSite;
      ResponseCookie.Rfc6265Utils.validateCookieName(name);
      ResponseCookie.Rfc6265Utils.validateCookieValue(value);
      ResponseCookie.Rfc6265Utils.validateDomain(domain);
      ResponseCookie.Rfc6265Utils.validatePath(path);
      ResponseCookie.Rfc6265Utils.validateSameSite(sameSite);
   }

   public Duration getMaxAge() {
      return this.maxAge;
   }

   public @Nullable String getDomain() {
      return this.domain;
   }

   public @Nullable String getPath() {
      return this.path;
   }

   public boolean isSecure() {
      return this.secure;
   }

   public boolean isHttpOnly() {
      return this.httpOnly;
   }

   public boolean isPartitioned() {
      return this.partitioned;
   }

   public @Nullable String getSameSite() {
      return this.sameSite;
   }

   public ResponseCookie.ResponseCookieBuilder mutate() {
      return new ResponseCookie.DefaultResponseCookieBuilder(this.getName(), this.getValue(), false)
         .maxAge(this.maxAge)
         .domain(this.domain)
         .path(this.path)
         .secure(this.secure)
         .httpOnly(this.httpOnly)
         .partitioned(this.partitioned)
         .sameSite(this.sameSite);
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof ResponseCookie that
            && this.getName().equalsIgnoreCase(that.getName())
            && ObjectUtils.nullSafeEquals(this.path, that.getPath())
            && ObjectUtils.nullSafeEquals(this.domain, that.getDomain());
   }

   @Override
   public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + ObjectUtils.nullSafeHashCode(this.domain);
      return 31 * result + ObjectUtils.nullSafeHashCode(this.path);
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(this.getName()).append('=').append(this.getValue());
      if (StringUtils.hasText(this.getPath())) {
         sb.append("; Path=").append(this.getPath());
      }

      if (StringUtils.hasText(this.domain)) {
         sb.append("; Domain=").append(this.domain);
      }

      if (!this.maxAge.isNegative()) {
         sb.append("; Max-Age=").append(this.maxAge.getSeconds());
         sb.append("; Expires=");
         long millis = this.maxAge.getSeconds() > 0L ? System.currentTimeMillis() + this.maxAge.toMillis() : 0L;
         sb.append(HttpHeaders.formatDate(millis));
      }

      if (this.secure) {
         sb.append("; Secure");
      }

      if (this.httpOnly) {
         sb.append("; HttpOnly");
      }

      if (this.partitioned) {
         sb.append("; Partitioned");
      }

      if (StringUtils.hasText(this.sameSite)) {
         sb.append("; SameSite=").append(this.sameSite);
      }

      return sb.toString();
   }

   public static ResponseCookie.ResponseCookieBuilder from(String name) {
      return new ResponseCookie.DefaultResponseCookieBuilder(name, null, false);
   }

   public static ResponseCookie.ResponseCookieBuilder from(String name, @Nullable String value) {
      return new ResponseCookie.DefaultResponseCookieBuilder(name, value, false);
   }

   public static ResponseCookie.ResponseCookieBuilder fromClientResponse(String name, @Nullable String value) {
      return new ResponseCookie.DefaultResponseCookieBuilder(name, value, true);
   }

   public static ResponseCookie.ResponseCookieBuilder from(java.net.HttpCookie cookie) {
      return from(cookie.getName(), cookie.getValue())
         .domain(cookie.getDomain())
         .httpOnly(cookie.isHttpOnly())
         .maxAge(cookie.getMaxAge())
         .path(cookie.getPath())
         .secure(cookie.getSecure());
   }

   private static class DefaultResponseCookieBuilder implements ResponseCookie.ResponseCookieBuilder {
      private final String name;
      private @Nullable String value;
      private final boolean lenient;
      private Duration maxAge = Duration.ofSeconds(-1L);
      private @Nullable String domain;
      private @Nullable String path;
      private boolean secure;
      private boolean httpOnly;
      private boolean partitioned;
      private @Nullable String sameSite;

      DefaultResponseCookieBuilder(String name, @Nullable String value, boolean lenient) {
         this.name = name;
         this.value = value;
         this.lenient = lenient;
      }

      @Override
      public ResponseCookie.ResponseCookieBuilder value(@Nullable String value) {
         this.value = value;
         return this;
      }

      @Override
      public ResponseCookie.ResponseCookieBuilder maxAge(Duration maxAge) {
         this.maxAge = maxAge;
         return this;
      }

      @Override
      public ResponseCookie.ResponseCookieBuilder maxAge(long maxAgeSeconds) {
         this.maxAge = maxAgeSeconds >= 0L ? Duration.ofSeconds(maxAgeSeconds) : Duration.ofSeconds(-1L);
         return this;
      }

      @Override
      public ResponseCookie.ResponseCookieBuilder domain(@Nullable String domain) {
         this.domain = this.initDomain(domain);
         return this;
      }

      private @Nullable String initDomain(@Nullable String domain) {
         if (this.lenient && StringUtils.hasLength(domain)) {
            String str = domain.trim();
            if (str.startsWith("\"") && str.endsWith("\"") && str.substring(1, str.length() - 1).trim().isEmpty()) {
               return null;
            }
         }

         return domain;
      }

      @Override
      public ResponseCookie.ResponseCookieBuilder path(@Nullable String path) {
         this.path = path;
         return this;
      }

      @Override
      public ResponseCookie.ResponseCookieBuilder secure(boolean secure) {
         this.secure = secure;
         return this;
      }

      @Override
      public ResponseCookie.ResponseCookieBuilder httpOnly(boolean httpOnly) {
         this.httpOnly = httpOnly;
         return this;
      }

      @Override
      public ResponseCookie.ResponseCookieBuilder partitioned(boolean partitioned) {
         this.partitioned = partitioned;
         return this;
      }

      @Override
      public ResponseCookie.ResponseCookieBuilder sameSite(@Nullable String sameSite) {
         this.sameSite = sameSite;
         return this;
      }

      @Override
      public ResponseCookie build() {
         return new ResponseCookie(this.name, this.value, this.maxAge, this.domain, this.path, this.secure, this.httpOnly, this.partitioned, this.sameSite);
      }
   }

   public interface Parser {
      List<ResponseCookie> parse(String header);

      default MultiValueMap<String, ResponseCookie> parse(List<String> headers) {
         MultiValueMap<String, ResponseCookie> result = new LinkedMultiValueMap();

         for (String header : headers) {
            for (ResponseCookie cookie : this.parse(header)) {
               result.add(cookie.getName(), cookie);
            }
         }

         return result;
      }
   }

   public interface ResponseCookieBuilder {
      ResponseCookie.ResponseCookieBuilder value(@Nullable String value);

      ResponseCookie.ResponseCookieBuilder maxAge(Duration maxAge);

      ResponseCookie.ResponseCookieBuilder maxAge(long maxAgeSeconds);

      ResponseCookie.ResponseCookieBuilder path(@Nullable String path);

      ResponseCookie.ResponseCookieBuilder domain(@Nullable String domain);

      ResponseCookie.ResponseCookieBuilder secure(boolean secure);

      ResponseCookie.ResponseCookieBuilder httpOnly(boolean httpOnly);

      ResponseCookie.ResponseCookieBuilder partitioned(boolean partitioned);

      ResponseCookie.ResponseCookieBuilder sameSite(@Nullable String sameSite);

      ResponseCookie build();
   }

   private static class Rfc6265Utils {
      private static final String SEPARATOR_CHARS = new String(
         new char[]{'(', ')', '<', '>', '@', ',', ';', ':', '\\', '"', '/', '[', ']', '?', '=', '{', '}', ' '}
      );
      private static final String DOMAIN_CHARS = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ.-";

      public static void validateCookieName(String name) {
         for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c <= 31 || c == 127) {
               throw new IllegalArgumentException(name + ": RFC2616 token cannot have control chars");
            }

            if (SEPARATOR_CHARS.indexOf(c) >= 0) {
               throw new IllegalArgumentException(name + ": RFC2616 token cannot have separator chars such as '" + c + "'");
            }

            if (c >= 128) {
               throw new IllegalArgumentException(name + ": RFC2616 token can only have US-ASCII: 0x" + Integer.toHexString(c));
            }
         }
      }

      public static void validateCookieValue(@Nullable String value) {
         if (value != null) {
            int start = 0;
            int end = value.length();
            if (end > 1 && value.charAt(0) == '"' && value.charAt(end - 1) == '"') {
               start = 1;
               end--;
            }

            for (int i = start; i < end; i++) {
               char c = value.charAt(i);
               if (c < '!' || c == '"' || c == ',' || c == ';' || c == '\\' || c == 127) {
                  throw new IllegalArgumentException("RFC2616 cookie value cannot have '" + c + "'");
               }

               if (c >= 128) {
                  throw new IllegalArgumentException("RFC2616 cookie value can only have US-ASCII chars: 0x" + Integer.toHexString(c));
               }
            }
         }
      }

      public static void validateDomain(@Nullable String domain) {
         if (StringUtils.hasLength(domain)) {
            int char1 = domain.charAt(0);
            int charN = domain.charAt(domain.length() - 1);
            if (char1 != 45 && charN != 46 && charN != 45) {
               int i = 0;
               int c = -1;

               while (i < domain.length()) {
                  int p = c;
                  c = domain.charAt(i);
                  if ("0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ.-".indexOf(c) == -1
                     || p == 46 && (c == 46 || c == 45)
                     || p == 45 && c == 46) {
                     throw new IllegalArgumentException(domain + ": invalid cookie domain char '" + c + "'");
                  }

                  i++;
               }
            } else {
               throw new IllegalArgumentException("Invalid first/last char in cookie domain: " + domain);
            }
         }
      }

      public static void validatePath(@Nullable String path) {
         if (path != null) {
            for (int i = 0; i < path.length(); i++) {
               char c = path.charAt(i);
               if (c < ' ' || c > '~' || c == ';') {
                  throw new IllegalArgumentException(path + ": Invalid cookie path char '" + c + "'");
               }
            }
         }
      }

      public static void validateSameSite(@Nullable String sameSite) {
         if (sameSite != null) {
            for (int i = 0; i < sameSite.length(); i++) {
               char c = sameSite.charAt(i);
               if (c < ' ' || c > '~' || c == ';') {
                  throw new IllegalArgumentException(sameSite + ": Invalid cookie SameSite char '" + c + "'");
               }
            }
         }
      }
   }
}
