package org.springframework.http;

import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.AbstractSet;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.StringJoiner;
import java.util.Locale.LanguageRange;
import java.util.Map.Entry;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedCaseInsensitiveMap;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public class HttpHeaders implements Serializable {
   private static final long serialVersionUID = -8578554704772377436L;
   public static final String ACCEPT = "Accept";
   public static final String ACCEPT_CHARSET = "Accept-Charset";
   public static final String ACCEPT_ENCODING = "Accept-Encoding";
   public static final String ACCEPT_LANGUAGE = "Accept-Language";
   public static final String ACCEPT_PATCH = "Accept-Patch";
   public static final String ACCEPT_QUERY = "Accept-Query";
   public static final String ACCEPT_RANGES = "Accept-Ranges";
   public static final String ACCESS_CONTROL_ALLOW_CREDENTIALS = "Access-Control-Allow-Credentials";
   public static final String ACCESS_CONTROL_ALLOW_HEADERS = "Access-Control-Allow-Headers";
   public static final String ACCESS_CONTROL_ALLOW_METHODS = "Access-Control-Allow-Methods";
   public static final String ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
   public static final String ACCESS_CONTROL_EXPOSE_HEADERS = "Access-Control-Expose-Headers";
   public static final String ACCESS_CONTROL_MAX_AGE = "Access-Control-Max-Age";
   public static final String ACCESS_CONTROL_REQUEST_HEADERS = "Access-Control-Request-Headers";
   public static final String ACCESS_CONTROL_REQUEST_METHOD = "Access-Control-Request-Method";
   public static final String AGE = "Age";
   public static final String ALLOW = "Allow";
   public static final String AUTHORIZATION = "Authorization";
   public static final String CACHE_CONTROL = "Cache-Control";
   public static final String CONNECTION = "Connection";
   public static final String CONTENT_ENCODING = "Content-Encoding";
   public static final String CONTENT_DISPOSITION = "Content-Disposition";
   public static final String CONTENT_LANGUAGE = "Content-Language";
   public static final String CONTENT_LENGTH = "Content-Length";
   public static final String CONTENT_LOCATION = "Content-Location";
   public static final String CONTENT_RANGE = "Content-Range";
   public static final String CONTENT_TYPE = "Content-Type";
   public static final String COOKIE = "Cookie";
   public static final String DATE = "Date";
   public static final String ETAG = "ETag";
   public static final String EXPECT = "Expect";
   public static final String EXPIRES = "Expires";
   public static final String FROM = "From";
   public static final String HOST = "Host";
   public static final String IF_MATCH = "If-Match";
   public static final String IF_MODIFIED_SINCE = "If-Modified-Since";
   public static final String IF_NONE_MATCH = "If-None-Match";
   public static final String IF_RANGE = "If-Range";
   public static final String IF_UNMODIFIED_SINCE = "If-Unmodified-Since";
   public static final String LAST_MODIFIED = "Last-Modified";
   public static final String LINK = "Link";
   public static final String LOCATION = "Location";
   public static final String MAX_FORWARDS = "Max-Forwards";
   public static final String ORIGIN = "Origin";
   public static final String PRAGMA = "Pragma";
   public static final String PROXY_AUTHENTICATE = "Proxy-Authenticate";
   public static final String PROXY_AUTHORIZATION = "Proxy-Authorization";
   public static final String RANGE = "Range";
   public static final String REFERER = "Referer";
   public static final String RETRY_AFTER = "Retry-After";
   public static final String SERVER = "Server";
   public static final String SET_COOKIE = "Set-Cookie";
   public static final String SET_COOKIE2 = "Set-Cookie2";
   public static final String TE = "TE";
   public static final String TRAILER = "Trailer";
   public static final String TRANSFER_ENCODING = "Transfer-Encoding";
   public static final String UPGRADE = "Upgrade";
   public static final String USER_AGENT = "User-Agent";
   public static final String VARY = "Vary";
   public static final String VIA = "Via";
   public static final String WARNING = "Warning";
   public static final String WWW_AUTHENTICATE = "WWW-Authenticate";
   public static final HttpHeaders EMPTY = new ReadOnlyHttpHeaders(new LinkedMultiValueMap());
   private static final DecimalFormatSymbols DECIMAL_FORMAT_SYMBOLS = new DecimalFormatSymbols(Locale.ROOT);
   private static final ZoneId GMT = ZoneId.of("GMT");
   private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US).withZone(GMT);
   private static final DateTimeFormatter[] DATE_PARSERS = new DateTimeFormatter[]{
      DateTimeFormatter.RFC_1123_DATE_TIME,
      DateTimeFormatter.ofPattern("EEEE, dd-MMM-yy HH:mm:ss zzz", Locale.US),
      DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss yyyy", Locale.US).withZone(GMT),
      new DateTimeFormatterBuilder().appendLiteral('@').appendValue(ChronoField.INSTANT_SECONDS).toFormatter(Locale.US).withZone(GMT)
   };
   final MultiValueMap<String, String> headers;

   public HttpHeaders() {
      this(CollectionUtils.toMultiValueMap(new LinkedCaseInsensitiveMap(8, Locale.ROOT)));
   }

   public HttpHeaders(MultiValueMap<String, String> headers) {
      Assert.notNull(headers, "MultiValueMap must not be null");
      this.headers = headers;
   }

   public HttpHeaders(HttpHeaders httpHeaders) {
      Assert.notNull(httpHeaders, "HttpHeaders must not be null");
      this.headers = httpHeaders == EMPTY ? CollectionUtils.toMultiValueMap(new LinkedCaseInsensitiveMap(8, Locale.ENGLISH)) : unwrap(httpHeaders);
   }

   public static HttpHeaders copyOf(MultiValueMap<String, String> headers) {
      HttpHeaders httpHeadersCopy = new HttpHeaders();

      for (String name : headers.keySet()) {
         List<String> values = (List<String>)headers.get(name);
         if (values != null) {
            httpHeadersCopy.put(name, new ArrayList<>(values));
         }
      }

      return httpHeadersCopy;
   }

   public static HttpHeaders copyOf(HttpHeaders httpHeaders) {
      return copyOf(httpHeaders.headers);
   }

   public List<String> getOrEmpty(String headerName) {
      return this.getOrDefault(headerName, Collections.emptyList());
   }

   public List<String> getOrDefault(String headerName, List<String> defaultValue) {
      List<String> values = this.get(headerName);
      return values != null ? values : defaultValue;
   }

   public void setAccept(List<MediaType> acceptableMediaTypes) {
      this.set("Accept", MediaType.toString(acceptableMediaTypes));
   }

   public List<MediaType> getAccept() {
      return MediaType.parseMediaTypes(this.get("Accept"));
   }

   public void setAcceptLanguage(List<LanguageRange> languages) {
      Assert.notNull(languages, "LanguageRange List must not be null");
      DecimalFormat decimal = new DecimalFormat("0.0", DECIMAL_FORMAT_SYMBOLS);
      List<String> values = languages.stream()
         .map(range -> range.getWeight() == 1.0 ? range.getRange() : range.getRange() + ";q=" + decimal.format(range.getWeight()))
         .toList();
      this.set("Accept-Language", this.toCommaDelimitedString(values));
   }

   public List<LanguageRange> getAcceptLanguage() {
      String value = this.getFirst("Accept-Language");
      if (StringUtils.hasText(value)) {
         try {
            return LanguageRange.parse(value);
         } catch (IllegalArgumentException ignored) {
            String[] tokens = StringUtils.tokenizeToStringArray(value, ",");

            for (int i = 0; i < tokens.length; i++) {
               tokens[i] = StringUtils.trimTrailingCharacter(tokens[i], ';');
            }

            value = StringUtils.arrayToCommaDelimitedString(tokens);
            return LanguageRange.parse(value);
         }
      } else {
         return Collections.emptyList();
      }
   }

   public void setAcceptLanguageAsLocales(List<Locale> locales) {
      this.setAcceptLanguage(locales.stream().map(locale -> new LanguageRange(locale.toLanguageTag())).toList());
   }

   public List<Locale> getAcceptLanguageAsLocales() {
      List<LanguageRange> ranges = this.getAcceptLanguage();
      if (ranges.isEmpty()) {
         return Collections.emptyList();
      }

      List<Locale> locales = new ArrayList<>(ranges.size());

      for (LanguageRange range : ranges) {
         if (!range.getRange().startsWith("*")) {
            locales.add(Locale.forLanguageTag(range.getRange()));
         }
      }

      return locales;
   }

   public void setAcceptPatch(List<MediaType> mediaTypes) {
      this.set("Accept-Patch", MediaType.toString(mediaTypes));
   }

   public List<MediaType> getAcceptPatch() {
      return MediaType.parseMediaTypes(this.get("Accept-Patch"));
   }

   public void setAcceptQuery(List<MediaType> mediaTypes) {
      this.set("Accept-Query", MediaType.toString(mediaTypes));
   }

   public List<MediaType> getAcceptQuery() {
      return MediaType.parseMediaTypes(this.get("Accept-Query"));
   }

   public void setAccessControlAllowCredentials(boolean allowCredentials) {
      this.set("Access-Control-Allow-Credentials", Boolean.toString(allowCredentials));
   }

   public boolean getAccessControlAllowCredentials() {
      return Boolean.parseBoolean(this.getFirst("Access-Control-Allow-Credentials"));
   }

   public void setAccessControlAllowHeaders(List<String> allowedHeaders) {
      this.set("Access-Control-Allow-Headers", this.toCommaDelimitedString(allowedHeaders));
   }

   public List<String> getAccessControlAllowHeaders() {
      return this.getValuesAsList("Access-Control-Allow-Headers");
   }

   public void setAccessControlAllowMethods(List<HttpMethod> allowedMethods) {
      this.set("Access-Control-Allow-Methods", StringUtils.collectionToCommaDelimitedString(allowedMethods));
   }

   public List<HttpMethod> getAccessControlAllowMethods() {
      String value = this.getFirst("Access-Control-Allow-Methods");
      if (value == null) {
         return Collections.emptyList();
      }

      String[] tokens = StringUtils.tokenizeToStringArray(value, ",");
      List<HttpMethod> result = new ArrayList<>(tokens.length);

      for (String token : tokens) {
         HttpMethod method = HttpMethod.valueOf(token);
         result.add(method);
      }

      return result;
   }

   public void setAccessControlAllowOrigin(@Nullable String allowedOrigin) {
      this.setOrRemove("Access-Control-Allow-Origin", allowedOrigin);
   }

   public @Nullable String getAccessControlAllowOrigin() {
      return this.getFieldValues("Access-Control-Allow-Origin");
   }

   public void setAccessControlExposeHeaders(List<String> exposedHeaders) {
      this.set("Access-Control-Expose-Headers", this.toCommaDelimitedString(exposedHeaders));
   }

   public List<String> getAccessControlExposeHeaders() {
      return this.getValuesAsList("Access-Control-Expose-Headers");
   }

   public void setAccessControlMaxAge(Duration maxAge) {
      this.set("Access-Control-Max-Age", Long.toString(maxAge.getSeconds()));
   }

   public void setAccessControlMaxAge(long maxAge) {
      this.set("Access-Control-Max-Age", Long.toString(maxAge));
   }

   public long getAccessControlMaxAge() {
      String value = this.getFirst("Access-Control-Max-Age");
      return value != null ? Long.parseLong(value) : -1L;
   }

   public void setAccessControlRequestHeaders(List<String> requestHeaders) {
      this.set("Access-Control-Request-Headers", this.toCommaDelimitedString(requestHeaders));
   }

   public List<String> getAccessControlRequestHeaders() {
      return this.getValuesAsList("Access-Control-Request-Headers");
   }

   public void setAccessControlRequestMethod(@Nullable HttpMethod requestMethod) {
      this.setOrRemove("Access-Control-Request-Method", requestMethod != null ? requestMethod.name() : null);
   }

   public @Nullable HttpMethod getAccessControlRequestMethod() {
      String requestMethod = this.getFirst("Access-Control-Request-Method");
      return requestMethod != null ? HttpMethod.valueOf(requestMethod) : null;
   }

   public void setAcceptCharset(List<Charset> acceptableCharsets) {
      StringJoiner joiner = new StringJoiner(", ");

      for (Charset charset : acceptableCharsets) {
         joiner.add(charset.name().toLowerCase(Locale.ROOT));
      }

      this.set("Accept-Charset", joiner.toString());
   }

   public List<Charset> getAcceptCharset() {
      String value = this.getFirst("Accept-Charset");
      if (value != null) {
         String[] tokens = StringUtils.tokenizeToStringArray(value, ",");
         List<Charset> result = new ArrayList<>(tokens.length);

         for (String token : tokens) {
            int paramIdx = token.indexOf(59);
            String charsetName;
            if (paramIdx == -1) {
               charsetName = token;
            } else {
               charsetName = token.substring(0, paramIdx);
            }

            if (!charsetName.equals("*")) {
               result.add(Charset.forName(charsetName));
            }
         }

         return result;
      } else {
         return Collections.emptyList();
      }
   }

   public void setAllow(Set<HttpMethod> allowedMethods) {
      this.set("Allow", StringUtils.collectionToCommaDelimitedString(allowedMethods));
   }

   public Set<HttpMethod> getAllow() {
      String value = this.getFirst("Allow");
      if (!StringUtils.hasLength(value)) {
         return Collections.emptySet();
      }

      String[] tokens = StringUtils.tokenizeToStringArray(value, ",");
      Set<HttpMethod> result = CollectionUtils.newLinkedHashSet(tokens.length);

      for (String token : tokens) {
         HttpMethod method = HttpMethod.valueOf(token);
         result.add(method);
      }

      return result;
   }

   public void setBasicAuth(String username, String password) {
      this.setBasicAuth(username, password, null);
   }

   public void setBasicAuth(String username, String password, @Nullable Charset charset) {
      this.setBasicAuth(encodeBasicAuth(username, password, charset));
   }

   public void setBasicAuth(String encodedCredentials) {
      Assert.hasText(encodedCredentials, "'encodedCredentials' must not be null or blank");
      this.set("Authorization", "Basic " + encodedCredentials);
   }

   public void setBearerAuth(String token) {
      this.set("Authorization", "Bearer " + token);
   }

   public void setCacheControl(CacheControl cacheControl) {
      this.setOrRemove("Cache-Control", cacheControl.getHeaderValue());
   }

   public void setCacheControl(@Nullable String cacheControl) {
      this.setOrRemove("Cache-Control", cacheControl);
   }

   public @Nullable String getCacheControl() {
      return this.getFieldValues("Cache-Control");
   }

   public void setConnection(String connection) {
      this.set("Connection", connection);
   }

   public void setConnection(List<String> connection) {
      this.set("Connection", this.toCommaDelimitedString(connection));
   }

   public List<String> getConnection() {
      return this.getValuesAsList("Connection");
   }

   public void setContentDispositionFormData(String name, @Nullable String filename) {
      Assert.notNull(name, "Name must not be null");
      ContentDisposition.Builder disposition = ContentDisposition.formData().name(name);
      if (StringUtils.hasText(filename)) {
         disposition.filename(filename);
      }

      this.setContentDisposition(disposition.build());
   }

   public void setContentDisposition(ContentDisposition contentDisposition) {
      this.set("Content-Disposition", contentDisposition.toString());
   }

   public ContentDisposition getContentDisposition() {
      String contentDisposition = this.getFirst("Content-Disposition");
      return StringUtils.hasText(contentDisposition) ? ContentDisposition.parse(contentDisposition) : ContentDisposition.empty();
   }

   public void setContentLanguage(@Nullable Locale locale) {
      this.setOrRemove("Content-Language", locale != null ? locale.toLanguageTag() : null);
   }

   public @Nullable Locale getContentLanguage() {
      return this.getValuesAsList("Content-Language").stream().findFirst().map(Locale::forLanguageTag).orElse(null);
   }

   public void setContentLength(long contentLength) {
      if (contentLength < 0L) {
         throw new IllegalArgumentException("Content-Length must be a non-negative number");
      }

      this.set("Content-Length", Long.toString(contentLength));
   }

   public long getContentLength() {
      String value = this.getFirst("Content-Length");
      return value != null ? Long.parseLong(value) : -1L;
   }

   public void setContentType(@Nullable MediaType mediaType) {
      if (mediaType != null) {
         Assert.isTrue(!mediaType.isWildcardType(), "Content-Type cannot contain wildcard type '*'");
         Assert.isTrue(!mediaType.isWildcardSubtype(), "Content-Type cannot contain wildcard subtype '*'");
         this.set("Content-Type", mediaType.toString());
      } else {
         this.remove("Content-Type");
      }
   }

   public @Nullable MediaType getContentType() {
      String value = this.getFirst("Content-Type");
      return StringUtils.hasLength(value) ? MediaType.parseMediaType(value) : null;
   }

   public void setDate(ZonedDateTime date) {
      this.setZonedDateTime("Date", date);
   }

   public void setDate(Instant date) {
      this.setInstant("Date", date);
   }

   public void setDate(long date) {
      this.setDate("Date", date);
   }

   public long getDate() {
      return this.getFirstDate("Date");
   }

   public void setETag(@Nullable String tag) {
      if (tag != null) {
         this.set("ETag", ETag.quoteETagIfNecessary(tag));
      } else {
         this.remove("ETag");
      }
   }

   public @Nullable String getETag() {
      return this.getFirst("ETag");
   }

   public void setExpires(ZonedDateTime expires) {
      this.setZonedDateTime("Expires", expires);
   }

   public void setExpires(Instant expires) {
      this.setInstant("Expires", expires);
   }

   public void setExpires(long expires) {
      this.setDate("Expires", expires);
   }

   public long getExpires() {
      return this.getFirstDate("Expires", false);
   }

   public void setHost(@Nullable InetSocketAddress host) {
      if (host != null) {
         String value = host.getHostString();
         int port = host.getPort();
         if (port != 0) {
            value = value + ":" + port;
         }

         this.set("Host", value);
      } else {
         this.remove("Host");
      }
   }

   public @Nullable InetSocketAddress getHost() {
      String value = this.getFirst("Host");
      if (value == null) {
         return null;
      }

      String host = null;
      int port = 0;
      int separator = value.startsWith("[") ? value.indexOf(58, value.indexOf(93)) : value.lastIndexOf(58);
      if (separator != -1) {
         host = value.substring(0, separator);
         String portString = value.substring(separator + 1);

         try {
            port = Integer.parseInt(portString);
         } catch (NumberFormatException var7) {
         }
      }

      if (host == null) {
         host = value;
      }

      return InetSocketAddress.createUnresolved(host, port);
   }

   public void setIfMatch(String ifMatch) {
      this.set("If-Match", ifMatch);
   }

   public void setIfMatch(List<String> ifMatchList) {
      this.set("If-Match", this.toCommaDelimitedString(ifMatchList));
   }

   public List<String> getIfMatch() {
      return this.getETagValuesAsList("If-Match");
   }

   public void setIfModifiedSince(ZonedDateTime ifModifiedSince) {
      this.setZonedDateTime("If-Modified-Since", ifModifiedSince.withZoneSameInstant(GMT));
   }

   public void setIfModifiedSince(Instant ifModifiedSince) {
      this.setInstant("If-Modified-Since", ifModifiedSince);
   }

   public void setIfModifiedSince(long ifModifiedSince) {
      this.setDate("If-Modified-Since", ifModifiedSince);
   }

   public long getIfModifiedSince() {
      return this.getFirstDate("If-Modified-Since", false);
   }

   public void setIfNoneMatch(String ifNoneMatch) {
      this.set("If-None-Match", ifNoneMatch);
   }

   public void setIfNoneMatch(List<String> ifNoneMatchList) {
      this.set("If-None-Match", this.toCommaDelimitedString(ifNoneMatchList));
   }

   public List<String> getIfNoneMatch() {
      return this.getETagValuesAsList("If-None-Match");
   }

   public void setIfUnmodifiedSince(ZonedDateTime ifUnmodifiedSince) {
      this.setZonedDateTime("If-Unmodified-Since", ifUnmodifiedSince.withZoneSameInstant(GMT));
   }

   public void setIfUnmodifiedSince(Instant ifUnmodifiedSince) {
      this.setInstant("If-Unmodified-Since", ifUnmodifiedSince);
   }

   public void setIfUnmodifiedSince(long ifUnmodifiedSince) {
      this.setDate("If-Unmodified-Since", ifUnmodifiedSince);
   }

   public long getIfUnmodifiedSince() {
      return this.getFirstDate("If-Unmodified-Since", false);
   }

   public void setLastModified(ZonedDateTime lastModified) {
      this.setZonedDateTime("Last-Modified", lastModified.withZoneSameInstant(GMT));
   }

   public void setLastModified(Instant lastModified) {
      this.setInstant("Last-Modified", lastModified);
   }

   public void setLastModified(long lastModified) {
      this.setDate("Last-Modified", lastModified);
   }

   public long getLastModified() {
      return this.getFirstDate("Last-Modified", false);
   }

   public void setLocation(@Nullable URI location) {
      this.setOrRemove("Location", location != null ? location.toASCIIString() : null);
   }

   public @Nullable URI getLocation() {
      String value = this.getFirst("Location");
      return value != null ? URI.create(value) : null;
   }

   public void setOrigin(@Nullable String origin) {
      this.setOrRemove("Origin", origin);
   }

   public @Nullable String getOrigin() {
      return this.getFirst("Origin");
   }

   public void setPragma(@Nullable String pragma) {
      this.setOrRemove("Pragma", pragma);
   }

   public @Nullable String getPragma() {
      return this.getFirst("Pragma");
   }

   public void setRange(List<HttpRange> ranges) {
      String value = HttpRange.toString(ranges);
      this.set("Range", value);
   }

   public List<HttpRange> getRange() {
      String value = this.getFirst("Range");
      return HttpRange.parseRanges(value);
   }

   public void setUpgrade(@Nullable String upgrade) {
      this.setOrRemove("Upgrade", upgrade);
   }

   public @Nullable String getUpgrade() {
      return this.getFirst("Upgrade");
   }

   public void setVary(List<String> requestHeaders) {
      this.set("Vary", this.toCommaDelimitedString(requestHeaders));
   }

   public List<String> getVary() {
      return this.getValuesAsList("Vary");
   }

   public void setZonedDateTime(String headerName, ZonedDateTime date) {
      this.set(headerName, DATE_FORMATTER.format(date));
   }

   public void setInstant(String headerName, Instant date) {
      this.setZonedDateTime(headerName, date.atZone(GMT));
   }

   public void setDate(String headerName, long date) {
      this.setInstant(headerName, Instant.ofEpochMilli(date));
   }

   public long getFirstDate(String headerName) {
      return this.getFirstDate(headerName, true);
   }

   private long getFirstDate(String headerName, boolean rejectInvalid) {
      ZonedDateTime zonedDateTime = this.getFirstZonedDateTime(headerName, rejectInvalid);
      return zonedDateTime != null ? zonedDateTime.toInstant().toEpochMilli() : -1L;
   }

   public @Nullable ZonedDateTime getFirstZonedDateTime(String headerName) {
      return this.getFirstZonedDateTime(headerName, true);
   }

   private @Nullable ZonedDateTime getFirstZonedDateTime(String headerName, boolean rejectInvalid) {
      String headerValue = this.getFirst(headerName);
      if (headerValue == null) {
         return null;
      }

      if (headerValue.length() >= 3 || headerValue.startsWith("@")) {
         int parametersIndex = headerValue.indexOf(59);
         if (parametersIndex != -1) {
            headerValue = headerValue.substring(0, parametersIndex);
         }

         for (DateTimeFormatter dateFormatter : DATE_PARSERS) {
            try {
               return ZonedDateTime.parse(headerValue, dateFormatter);
            } catch (DateTimeParseException var10) {
            }
         }
      }

      if (rejectInvalid) {
         throw new IllegalArgumentException("Cannot parse date value \"" + headerValue + "\" for \"" + headerName + "\" header");
      } else {
         return null;
      }
   }

   public List<String> getValuesAsList(String headerName) {
      List<String> values = this.get(headerName);
      if (values != null) {
         List<String> result = new ArrayList<>();

         for (String value : values) {
            if (value != null) {
               result.addAll(tokenizeQuoted(value));
            }
         }

         return result;
      } else {
         return Collections.emptyList();
      }
   }

   private static List<String> tokenizeQuoted(String str) {
      List<String> tokens = new ArrayList<>();
      boolean quoted = false;
      boolean trim = true;
      StringBuilder builder = new StringBuilder(str.length());

      for (int i = 0; i < str.length(); i++) {
         char ch = str.charAt(i);
         if (ch == '"') {
            if (builder.isEmpty()) {
               quoted = true;
            } else if (quoted) {
               quoted = false;
               trim = false;
            } else {
               builder.append(ch);
            }
         } else if (ch == '\\' && quoted && i < str.length() - 1) {
            builder.append(str.charAt(++i));
         } else if (ch == ',' && !quoted) {
            addToken(builder, tokens, trim);
            builder.setLength(0);
            trim = false;
         } else if (quoted || !builder.isEmpty() && trim || !Character.isWhitespace(ch)) {
            builder.append(ch);
         }
      }

      if (!builder.isEmpty()) {
         addToken(builder, tokens, trim);
      }

      return tokens;
   }

   private static void addToken(StringBuilder builder, List<String> tokens, boolean trim) {
      String token = builder.toString();
      if (trim) {
         token = token.trim();
      }

      if (!token.isEmpty()) {
         tokens.add(token);
      }
   }

   public void clearContentHeaders() {
      this.remove("Content-Disposition");
      this.remove("Content-Encoding");
      this.remove("Content-Language");
      this.remove("Content-Length");
      this.remove("Content-Location");
      this.remove("Content-Range");
      this.remove("Content-Type");
   }

   protected List<String> getETagValuesAsList(String name) {
      List<String> values = this.get(name);
      if (values == null) {
         return Collections.emptyList();
      }

      List<String> result = new ArrayList<>();

      for (String value : values) {
         if (value != null) {
            List<ETag> tags = ETag.parse(value);
            Assert.notEmpty(tags, "Could not parse header '" + name + "' with value '" + value + "'");

            for (ETag tag : tags) {
               result.add(tag.formattedTag());
            }
         }
      }

      return result;
   }

   protected @Nullable String getFieldValues(String headerName) {
      List<String> headerValues = this.get(headerName);
      return headerValues != null ? this.toCommaDelimitedString(headerValues) : null;
   }

   protected String toCommaDelimitedString(List<String> headerValues) {
      StringJoiner joiner = new StringJoiner(", ");

      for (String val : headerValues) {
         if (val != null) {
            joiner.add(val);
         }
      }

      return joiner.toString();
   }

   private void setOrRemove(String headerName, @Nullable String headerValue) {
      if (headerValue != null) {
         this.set(headerName, headerValue);
      } else {
         this.remove(headerName);
      }
   }

   public @Nullable String getFirst(String headerName) {
      return (String)this.headers.getFirst(headerName);
   }

   public void add(String headerName, @Nullable String headerValue) {
      this.headers.add(headerName, headerValue);
   }

   public void addAll(String headerName, List<? extends String> headerValues) {
      this.headers.addAll(headerName, headerValues);
   }

   public void addAll(HttpHeaders headers) {
      headers.forEach(this::addAll);
   }

   public void set(String headerName, @Nullable String headerValue) {
      this.headers.set(headerName, headerValue);
   }

   public void setAll(Map<String, String> values) {
      this.headers.setAll(values);
   }

   public Map<String, String> toSingleValueMap() {
      return this.headers.toSingleValueMap();
   }

   @Deprecated(since = "7.0", forRemoval = true)
   public Map<String, String> asSingleValueMap() {
      return this.headers.asSingleValueMap();
   }

   @Deprecated(since = "7.0", forRemoval = true)
   public MultiValueMap<String, String> asMultiValueMap() {
      return this.headers;
   }

   public boolean isEmpty() {
      return this.headers.isEmpty();
   }

   public boolean containsHeader(String headerName) {
      return this.headers.containsKey(headerName);
   }

   public boolean hasHeaderValues(String headerName, List<String> values) {
      return ObjectUtils.nullSafeEquals(this.get(headerName), values);
   }

   public boolean containsHeaderValue(String headerName, String value) {
      List<String> values = this.get(headerName);
      return values == null ? false : values.contains(value);
   }

   public @Nullable List<String> get(String headerName) {
      return (List<String>)this.headers.get(headerName);
   }

   public @Nullable List<String> put(String headerName, List<String> headerValues) {
      return (List<String>)this.headers.put(headerName, headerValues);
   }

   public @Nullable List<String> putIfAbsent(String headerName, List<String> headerValues) {
      return (List<String>)this.headers.putIfAbsent(headerName, headerValues);
   }

   public void putAll(HttpHeaders headers) {
      headers.forEach(this::put);
   }

   public void putAll(Map<? extends String, ? extends List<String>> headers) {
      for (String name : headers.keySet()) {
         this.put(name, (List<String>)headers.get(name));
      }
   }

   public @Nullable List<String> remove(String key) {
      return (List<String>)this.headers.remove(key);
   }

   public void clear() {
      this.headers.clear();
   }

   public int size() {
      return this.headers.size();
   }

   public void forEach(BiConsumer<? super String, ? super List<String>> action) {
      this.headerSet().forEach(e -> action.accept(e.getKey(), e.getValue()));
   }

   public Set<Entry<String, List<String>>> headerSet() {
      return new HttpHeaders.CaseInsensitiveEntrySet(this.headers);
   }

   public Set<String> headerNames() {
      return new HttpHeaders.CaseInsensitiveHeaderNameSet(this.headers);
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof HttpHeaders that && unwrap(this).equals(unwrap(that));
   }

   @Override
   public int hashCode() {
      return this.headers.hashCode();
   }

   @Override
   public String toString() {
      return formatHeaders(this.headers);
   }

   public static HttpHeaders readOnlyHttpHeaders(MultiValueMap<String, String> headers) {
      return headers instanceof HttpHeaders httpHeaders ? readOnlyHttpHeaders(httpHeaders) : new ReadOnlyHttpHeaders(headers);
   }

   public static HttpHeaders readOnlyHttpHeaders(HttpHeaders headers) {
      Assert.notNull(headers, "HttpHeaders must not be null");
      return headers instanceof ReadOnlyHttpHeaders ? headers : new ReadOnlyHttpHeaders(headers.headers);
   }

   public static String formatHeaders(MultiValueMap<String, String> headers) {
      Set<String> headerNames = new HttpHeaders.CaseInsensitiveHeaderNameSet(headers);
      String suffix = "]";
      if (headerNames.size() != headers.size()) {
         suffix = "] with native header names " + headers.keySet();
      }

      return headerNames.stream()
         .map(
            headerName -> {
               List<String> values = (List<String>)headers.get(headerName);
               Assert.notNull(values, "Expected at least one value for header " + headerName);
               return headerName
                  + ":"
                  + (values.size() == 1 ? "\"" + values.get(0) + "\"" : values.stream().map(s -> "\"" + s + "\"").collect(Collectors.joining(", ")));
            }
         )
         .collect(Collectors.joining(", ", "[", suffix));
   }

   public static String encodeBasicAuth(String username, String password, @Nullable Charset charset) {
      Assert.notNull(username, "Username must not be null");
      Assert.doesNotContain(username, ":", "Username must not contain a colon");
      Assert.notNull(password, "Password must not be null");
      if (charset == null) {
         charset = StandardCharsets.ISO_8859_1;
      }

      CharsetEncoder encoder = charset.newEncoder();
      if (encoder.canEncode(username) && encoder.canEncode(password)) {
         String credentialsString = username + ":" + password;
         byte[] encodedBytes = Base64.getEncoder().encode(credentialsString.getBytes(charset));
         return new String(encodedBytes, charset);
      } else {
         throw new IllegalArgumentException("Username or password contains characters that cannot be encoded to " + charset.displayName());
      }
   }

   private static MultiValueMap<String, String> unwrap(HttpHeaders headers) {
      while (headers.headers instanceof HttpHeaders httpHeaders) {
         headers = httpHeaders;
      }

      return headers.headers;
   }

   static String formatDate(long date) {
      Instant instant = Instant.ofEpochMilli(date);
      ZonedDateTime time = instant.atZone(GMT);
      return DATE_FORMATTER.format(time);
   }

   private static final class CaseInsensitiveEntrySet extends AbstractSet<Entry<String, List<String>>> {
      private final MultiValueMap<String, String> headers;
      private final HttpHeaders.CaseInsensitiveHeaderNameSet nameSet;

      public CaseInsensitiveEntrySet(MultiValueMap<String, String> headers) {
         this.headers = headers;
         this.nameSet = new HttpHeaders.CaseInsensitiveHeaderNameSet(headers);
      }

      @Override
      public Iterator<Entry<String, List<String>>> iterator() {
         return new HttpHeaders.CaseInsensitiveEntrySet.CaseInsensitiveIterator(this.nameSet.iterator());
      }

      @Override
      public int size() {
         return this.nameSet.size();
      }

      private final class CaseInsensitiveEntry implements Entry<String, List<String>> {
         private final String key;

         CaseInsensitiveEntry(String key) {
            this.key = key;
         }

         public String getKey() {
            return this.key;
         }

         public List<String> getValue() {
            return Objects.requireNonNull((List<String>)CaseInsensitiveEntrySet.this.headers.get(this.key));
         }

         public List<String> setValue(List<String> value) {
            List<String> previous = Objects.requireNonNull((List<String>)CaseInsensitiveEntrySet.this.headers.get(this.key));
            CaseInsensitiveEntrySet.this.headers.put(this.key, value);
            return previous;
         }

         @Override
         public boolean equals(@Nullable Object other) {
            return this == other
               || other instanceof Entry<?, ?> that
                  && ObjectUtils.nullSafeEquals(this.getKey(), that.getKey())
                  && ObjectUtils.nullSafeEquals(this.getValue(), that.getValue());
         }

         @Override
         public int hashCode() {
            return this.key.hashCode();
         }
      }

      private final class CaseInsensitiveIterator implements Iterator<Entry<String, List<String>>> {
         private final Iterator<String> namesIterator;

         public CaseInsensitiveIterator(Iterator<String> namesIterator) {
            this.namesIterator = namesIterator;
         }

         @Override
         public boolean hasNext() {
            return this.namesIterator.hasNext();
         }

         public Entry<String, List<String>> next() {
            return CaseInsensitiveEntrySet.this.new CaseInsensitiveEntry(this.namesIterator.next());
         }

         @Override
         public void remove() {
            this.namesIterator.remove();
         }
      }
   }

   private static final class CaseInsensitiveHeaderNameSet extends AbstractSet<String> {
      private static final Object VALUE = new Object();
      private final MultiValueMap<String, String> headers;
      private final Map<String, Object> deduplicatedNames;

      public CaseInsensitiveHeaderNameSet(MultiValueMap<String, String> headers) {
         this.headers = headers;
         this.deduplicatedNames = new LinkedCaseInsensitiveMap(headers.size(), Locale.ROOT);

         for (String header : headers.keySet()) {
            if (!this.deduplicatedNames.containsKey(header)) {
               this.deduplicatedNames.put(header, VALUE);
            }
         }
      }

      @Override
      public Iterator<String> iterator() {
         return new HttpHeaders.HeaderNamesIterator(this.headers, this.deduplicatedNames);
      }

      @Override
      public int size() {
         return this.deduplicatedNames.size();
      }

      @Override
      public boolean contains(Object o) {
         return this.headers.containsKey(o);
      }

      @Override
      public boolean remove(Object o) {
         return this.headers.remove(o) != null && this.deduplicatedNames.remove(o) != null;
      }

      @Override
      public void clear() {
         this.headers.clear();
         this.deduplicatedNames.clear();
      }
   }

   private static class HeaderNamesIterator implements Iterator<String> {
      private final MultiValueMap<String, String> headers;
      private final Iterator<String> namesIterator;
      private @Nullable String currentName;

      public HeaderNamesIterator(MultiValueMap<String, String> headers, Map<String, Object> caseInsensitiveNames) {
         this.headers = headers;
         this.namesIterator = caseInsensitiveNames.keySet().iterator();
         this.currentName = null;
      }

      @Override
      public boolean hasNext() {
         return this.namesIterator.hasNext();
      }

      public String next() {
         this.currentName = this.namesIterator.next();
         return this.currentName;
      }

      @Override
      public void remove() {
         if (this.currentName == null) {
            throw new IllegalStateException("No current Header in iterator");
         }

         if (!this.headers.containsKey(this.currentName)) {
            throw new IllegalStateException("Header not present: " + this.currentName);
         }

         this.headers.remove(this.currentName);
      }
   }
}
