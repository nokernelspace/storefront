package org.springframework.http.server.reactive;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.RequestPath;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public abstract class AbstractServerHttpRequest implements ServerHttpRequest {
   private static final Pattern QUERY_PATTERN = Pattern.compile("([^&=]+)(=?)([^&]+)?");
   private final URI uri;
   private final @Nullable String contextPath;
   private @Nullable RequestPath path;
   private final HttpHeaders headers;
   private final HttpMethod method;
   private @Nullable MultiValueMap<String, String> queryParams;
   private @Nullable MultiValueMap<String, HttpCookie> cookies;
   private @Nullable SslInfo sslInfo;
   private @Nullable String id;
   private @Nullable String logPrefix;
   private @Nullable Supplier<Map<String, Object>> attributesSupplier;

   public AbstractServerHttpRequest(HttpMethod method, URI uri, @Nullable String contextPath, HttpHeaders headers) {
      Assert.notNull(method, "Method must not be null");
      Assert.notNull(uri, "Uri must not be null");
      Assert.notNull(headers, "Headers must not be null");
      this.method = method;
      this.uri = uri;
      this.contextPath = contextPath;
      this.headers = HttpHeaders.readOnlyHttpHeaders(headers);
   }

   @Override
   public String getId() {
      if (this.id == null) {
         this.id = this.initId();
         if (this.id == null) {
            this.id = ObjectUtils.getIdentityHexString(this);
         }
      }

      return this.id;
   }

   protected @Nullable String initId() {
      return null;
   }

   @Override
   public HttpMethod getMethod() {
      return this.method;
   }

   @Override
   public URI getURI() {
      return this.uri;
   }

   @Override
   public Map<String, Object> getAttributes() {
      return this.attributesSupplier != null ? this.attributesSupplier.get() : Collections.emptyMap();
   }

   @Override
   public RequestPath getPath() {
      if (this.path == null) {
         this.path = RequestPath.parse(this.uri, this.contextPath);
      }

      return this.path;
   }

   @Override
   public HttpHeaders getHeaders() {
      return this.headers;
   }

   @Override
   public MultiValueMap<String, String> getQueryParams() {
      if (this.queryParams == null) {
         this.queryParams = CollectionUtils.unmodifiableMultiValueMap(this.initQueryParams());
      }

      return this.queryParams;
   }

   protected MultiValueMap<String, String> initQueryParams() {
      MultiValueMap<String, String> queryParams = new LinkedMultiValueMap();
      String query = this.getURI().getRawQuery();
      if (query != null) {
         Matcher matcher = QUERY_PATTERN.matcher(query);

         while (matcher.find()) {
            String name = this.decodeQueryParam(matcher.group(1));
            String eq = matcher.group(2);
            String value = matcher.group(3);
            value = value != null ? this.decodeQueryParam(value) : (StringUtils.hasLength(eq) ? "" : null);
            queryParams.add(name, value);
         }
      }

      return queryParams;
   }

   private String decodeQueryParam(String value) {
      return URLDecoder.decode(value, StandardCharsets.UTF_8);
   }

   @Override
   public MultiValueMap<String, HttpCookie> getCookies() {
      if (this.cookies == null) {
         this.cookies = CollectionUtils.unmodifiableMultiValueMap(this.initCookies());
      }

      return this.cookies;
   }

   protected abstract MultiValueMap<String, HttpCookie> initCookies();

   @Override
   public @Nullable SslInfo getSslInfo() {
      if (this.sslInfo == null) {
         this.sslInfo = this.initSslInfo();
      }

      return this.sslInfo;
   }

   protected abstract @Nullable SslInfo initSslInfo();

   public abstract <T> T getNativeRequest();

   String getLogPrefix() {
      if (this.logPrefix == null) {
         this.logPrefix = "[" + this.initLogPrefix() + "] ";
      }

      return this.logPrefix;
   }

   protected String initLogPrefix() {
      return this.getId();
   }

   public void setAttributesSupplier(Supplier<Map<String, Object>> attributesSupplier) {
      this.attributesSupplier = attributesSupplier;
   }
}
