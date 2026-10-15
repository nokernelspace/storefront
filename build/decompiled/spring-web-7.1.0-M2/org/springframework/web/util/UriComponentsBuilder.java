package org.springframework.web.util;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public class UriComponentsBuilder implements UriBuilder, Cloneable {
   private static final Pattern QUERY_PARAM_PATTERN = Pattern.compile("([^&=]+)(=?)([^&]+)?");
   private static final Object[] EMPTY_VALUES = new Object[0];
   private @Nullable String scheme;
   private @Nullable String ssp;
   private @Nullable String userInfo;
   private @Nullable String host;
   private @Nullable String port;
   private UriComponentsBuilder.CompositePathComponentBuilder pathBuilder;
   private final MultiValueMap<String, String> queryParams = new LinkedMultiValueMap();
   private @Nullable String fragment;
   private final Map<String, Object> uriVariables = new HashMap<>(4);
   private boolean encodeTemplate;
   private Charset charset = StandardCharsets.UTF_8;

   protected UriComponentsBuilder() {
      this.pathBuilder = new UriComponentsBuilder.CompositePathComponentBuilder();
   }

   protected UriComponentsBuilder(UriComponentsBuilder other) {
      this.scheme = other.scheme;
      this.ssp = other.ssp;
      this.userInfo = other.userInfo;
      this.host = other.host;
      this.port = other.port;
      this.pathBuilder = other.pathBuilder.cloneBuilder();
      this.uriVariables.putAll(other.uriVariables);
      this.queryParams.addAll(other.queryParams);
      this.fragment = other.fragment;
      this.encodeTemplate = other.encodeTemplate;
      this.charset = other.charset;
   }

   public static UriComponentsBuilder newInstance() {
      return new UriComponentsBuilder();
   }

   public static UriComponentsBuilder fromPath(String path) {
      UriComponentsBuilder builder = new UriComponentsBuilder();
      builder.path(path);
      return builder;
   }

   public static UriComponentsBuilder fromUri(URI uri) {
      UriComponentsBuilder builder = new UriComponentsBuilder();
      builder.uri(uri);
      return builder;
   }

   public static UriComponentsBuilder fromUriString(String uri) throws InvalidUrlException {
      Assert.notNull(uri, "URI must not be null");
      return uri.isEmpty() ? new UriComponentsBuilder() : fromUriString(uri, UriComponentsBuilder.ParserType.RFC);
   }

   public static UriComponentsBuilder fromUriString(String uri, UriComponentsBuilder.ParserType parserType) throws InvalidUrlException {
      Assert.notNull(uri, "URI must not be null");
      if (uri.isEmpty()) {
         return new UriComponentsBuilder();
      }

      UriComponentsBuilder builder = new UriComponentsBuilder();

      return switch (parserType) {
         case RFC -> {
            RfcUriParser.UriRecord record = RfcUriParser.parse(uri);
            yield builder.rfcUriRecord(record);
         }
         case WHAT_WG -> {
            WhatWgUrlParser.UrlRecord record = WhatWgUrlParser.parse(uri, WhatWgUrlParser.EMPTY_RECORD, null, null);
            yield builder.whatWgUrlRecord(record);
         }
      };
   }

   public final UriComponentsBuilder encode() {
      return this.encode(StandardCharsets.UTF_8);
   }

   public UriComponentsBuilder encode(Charset charset) {
      this.encodeTemplate = true;
      this.charset = charset;
      return this;
   }

   public UriComponents build() {
      return this.build(false);
   }

   public UriComponents build(boolean encoded) {
      return this.buildInternal(
         encoded
            ? UriComponentsBuilder.EncodingHint.FULLY_ENCODED
            : (this.encodeTemplate ? UriComponentsBuilder.EncodingHint.ENCODE_TEMPLATE : UriComponentsBuilder.EncodingHint.NONE)
      );
   }

   private UriComponents buildInternal(UriComponentsBuilder.EncodingHint hint) {
      UriComponents result;
      if (this.ssp != null) {
         result = new OpaqueUriComponents(this.scheme, this.ssp, this.fragment);
      } else {
         MultiValueMap<String, String> queryParams = new LinkedMultiValueMap(this.queryParams);
         HierarchicalUriComponents uric = new HierarchicalUriComponents(
            this.scheme,
            this.fragment,
            this.userInfo,
            this.host,
            this.port,
            this.pathBuilder.build(),
            queryParams,
            hint == UriComponentsBuilder.EncodingHint.FULLY_ENCODED
         );
         result = hint == UriComponentsBuilder.EncodingHint.ENCODE_TEMPLATE ? uric.encodeTemplate(this.charset) : uric;
      }

      if (!this.uriVariables.isEmpty()) {
         result = result.expand(name -> this.uriVariables.getOrDefault(name, UriComponents.UriTemplateVariables.SKIP_VALUE));
      }

      return result;
   }

   public UriComponents buildAndExpand(Map<String, ?> uriVariables) {
      return this.build().expand((Map<String, ? extends Object>)uriVariables);
   }

   public UriComponents buildAndExpand(@Nullable Object... uriVariableValues) {
      return this.build().expand(uriVariableValues);
   }

   @Override
   public URI build(@Nullable Object... uriVariables) {
      return this.buildInternal(UriComponentsBuilder.EncodingHint.ENCODE_TEMPLATE).expand(uriVariables).toUri();
   }

   @Override
   public URI build(Map<String, ? extends @Nullable Object> uriVariables) {
      return this.buildInternal(UriComponentsBuilder.EncodingHint.ENCODE_TEMPLATE).expand(uriVariables).toUri();
   }

   @Override
   public String toUriString() {
      return this.uriVariables.isEmpty()
         ? this.build().encode().toUriString()
         : this.buildInternal(UriComponentsBuilder.EncodingHint.ENCODE_TEMPLATE).toUriString();
   }

   public UriComponentsBuilder uri(URI uri) {
      Assert.notNull(uri, "URI must not be null");
      this.scheme = uri.getScheme();
      if (uri.isOpaque()) {
         this.ssp = uri.getRawSchemeSpecificPart();
         this.resetHierarchicalComponents();
      } else {
         if (uri.getRawUserInfo() != null) {
            this.userInfo = uri.getRawUserInfo();
         }

         if (uri.getHost() != null) {
            this.host = uri.getHost();
         }

         if (uri.getPort() != -1) {
            this.port = String.valueOf(uri.getPort());
         }

         if (StringUtils.hasLength(uri.getRawPath())) {
            this.pathBuilder = new UriComponentsBuilder.CompositePathComponentBuilder();
            this.pathBuilder.addPath(uri.getRawPath());
         }

         if (StringUtils.hasLength(uri.getRawQuery())) {
            this.queryParams.clear();
            this.query(uri.getRawQuery());
         }

         this.resetSchemeSpecificPart();
      }

      if (uri.getRawFragment() != null) {
         this.fragment = uri.getRawFragment();
      }

      return this;
   }

   public UriComponentsBuilder uriComponents(UriComponents uriComponents) {
      Assert.notNull(uriComponents, "UriComponents must not be null");
      uriComponents.copyToUriComponentsBuilder(this);
      return this;
   }

   private UriComponentsBuilder rfcUriRecord(RfcUriParser.UriRecord record) {
      this.scheme(record.scheme());
      if (record.isOpaque()) {
         if (record.path() != null) {
            this.schemeSpecificPart(record.path());
         }
      } else {
         this.userInfo(record.user());
         this.host(record.host());
         this.port(record.port());
         if (record.path() != null) {
            this.path(record.path());
         }

         if (record.query() != null) {
            this.query(record.query());
         }
      }

      this.fragment(record.fragment());
      return this;
   }

   private UriComponentsBuilder whatWgUrlRecord(WhatWgUrlParser.UrlRecord record) {
      if (!record.scheme().isEmpty()) {
         this.scheme(record.scheme());
      }

      if (record.path().isOpaque()) {
         String ssp = record.path() + record.search();
         this.schemeSpecificPart(ssp);
      } else {
         this.userInfo(record.userInfo());
         String hostname = record.hostname();
         if (StringUtils.hasText(hostname)) {
            this.host(hostname);
         }

         if (record.port() != null) {
            this.port(record.portString());
         }

         this.path(record.path().toString());
         if (record.query() != null) {
            this.query(record.query());
         }
      }

      if (StringUtils.hasText(record.fragment())) {
         this.fragment(record.fragment());
      }

      return this;
   }

   public UriComponentsBuilder scheme(@Nullable String scheme) {
      this.scheme = scheme;
      return this;
   }

   public UriComponentsBuilder schemeSpecificPart(String ssp) {
      this.ssp = ssp;
      this.resetHierarchicalComponents();
      return this;
   }

   public UriComponentsBuilder userInfo(@Nullable String userInfo) {
      this.userInfo = userInfo;
      this.resetSchemeSpecificPart();
      return this;
   }

   public UriComponentsBuilder host(@Nullable String host) {
      this.host = host;
      if (host != null) {
         this.resetSchemeSpecificPart();
      }

      return this;
   }

   public UriComponentsBuilder port(int port) {
      Assert.isTrue(port >= -1, "Port must be >= -1");
      this.port = String.valueOf(port);
      if (port > -1) {
         this.resetSchemeSpecificPart();
      }

      return this;
   }

   public UriComponentsBuilder port(@Nullable String port) {
      this.port = StringUtils.hasText(port) ? port : null;
      if (port != null) {
         this.resetSchemeSpecificPart();
      }

      return this;
   }

   public UriComponentsBuilder path(String path) {
      this.pathBuilder.addPath(path);
      this.resetSchemeSpecificPart();
      return this;
   }

   public UriComponentsBuilder pathSegment(String... pathSegments) throws IllegalArgumentException {
      this.pathBuilder.addPathSegments(pathSegments);
      this.resetSchemeSpecificPart();
      return this;
   }

   public UriComponentsBuilder replacePath(@Nullable String path) {
      this.pathBuilder = new UriComponentsBuilder.CompositePathComponentBuilder();
      if (path != null) {
         this.pathBuilder.addPath(path);
      }

      this.resetSchemeSpecificPart();
      return this;
   }

   public UriComponentsBuilder query(String query) {
      if (StringUtils.hasText(query)) {
         Matcher matcher = QUERY_PARAM_PATTERN.matcher(query);

         while (matcher.find()) {
            String name = matcher.group(1);
            String eq = matcher.group(2);
            String value = matcher.group(3);
            this.queryParam(name, value != null ? value : (StringUtils.hasLength(eq) ? "" : null));
         }

         this.resetSchemeSpecificPart();
      }

      return this;
   }

   public UriComponentsBuilder replaceQuery(@Nullable String query) {
      this.queryParams.clear();
      if (query != null) {
         this.query(query);
         this.resetSchemeSpecificPart();
      }

      return this;
   }

   public UriComponentsBuilder queryParam(String name, @Nullable Object... values) {
      Assert.notNull(name, "Name must not be null");
      if (!ObjectUtils.isEmpty(values)) {
         for (Object value : values) {
            String valueAsString = this.getQueryParamValue(value);
            this.queryParams.add(name, valueAsString);
         }
      } else {
         this.queryParams.add(name, null);
      }

      this.resetSchemeSpecificPart();
      return this;
   }

   private @Nullable String getQueryParamValue(@Nullable Object value) {
      if (value != null) {
         return value instanceof Optional<?> optional ? optional.map(Object::toString).orElse(null) : value.toString();
      } else {
         return null;
      }
   }

   public UriComponentsBuilder queryParam(String name, @Nullable Collection<?> values) {
      return this.queryParam(name, CollectionUtils.isEmpty(values) ? EMPTY_VALUES : values.toArray());
   }

   public UriComponentsBuilder queryParamIfPresent(String name, Optional<?> value) {
      value.ifPresent(v -> {
         if (v instanceof Collection<?> values) {
            this.queryParam(name, values);
         } else {
            this.queryParam(name, v);
         }
      });
      return this;
   }

   public UriComponentsBuilder queryParams(@Nullable MultiValueMap<String, String> params) {
      if (params != null) {
         this.queryParams.addAll(params);
         this.resetSchemeSpecificPart();
      }

      return this;
   }

   public UriComponentsBuilder replaceQueryParam(String name, Object... values) {
      Assert.notNull(name, "Name must not be null");
      this.queryParams.remove(name);
      if (!ObjectUtils.isEmpty(values)) {
         this.queryParam(name, values);
      }

      this.resetSchemeSpecificPart();
      return this;
   }

   public UriComponentsBuilder replaceQueryParam(String name, @Nullable Collection<?> values) {
      return this.replaceQueryParam(name, CollectionUtils.isEmpty(values) ? EMPTY_VALUES : values.toArray());
   }

   public UriComponentsBuilder replaceQueryParams(@Nullable MultiValueMap<String, String> params) {
      this.queryParams.clear();
      if (params != null) {
         this.queryParams.putAll(params);
      }

      return this;
   }

   public UriComponentsBuilder fragment(@Nullable String fragment) {
      if (fragment != null) {
         Assert.hasLength(fragment, "Fragment must not be empty");
         this.fragment = fragment;
      } else {
         this.fragment = null;
      }

      return this;
   }

   public UriComponentsBuilder uriVariables(Map<String, Object> uriVariables) {
      this.uriVariables.putAll(uriVariables);
      return this;
   }

   private void resetHierarchicalComponents() {
      this.userInfo = null;
      this.host = null;
      this.port = null;
      this.pathBuilder = new UriComponentsBuilder.CompositePathComponentBuilder();
      this.queryParams.clear();
   }

   private void resetSchemeSpecificPart() {
      this.ssp = null;
   }

   void resetPortIfDefaultForScheme() {
      if (this.scheme != null
         && (
            (this.scheme.equals("http") || this.scheme.equals("ws")) && "80".equals(this.port)
               || (this.scheme.equals("https") || this.scheme.equals("wss")) && "443".equals(this.port)
         )) {
         this.port(null);
      }
   }

   @Override
   public Object clone() {
      return this.cloneBuilder();
   }

   public UriComponentsBuilder cloneBuilder() {
      return new UriComponentsBuilder(this);
   }

   private static class CompositePathComponentBuilder implements UriComponentsBuilder.PathComponentBuilder {
      private final Deque<UriComponentsBuilder.PathComponentBuilder> builders = new ArrayDeque<>();

      public void addPathSegments(String... pathSegments) {
         if (!ObjectUtils.isEmpty(pathSegments)) {
            UriComponentsBuilder.PathSegmentComponentBuilder psBuilder = this.getLastBuilder(UriComponentsBuilder.PathSegmentComponentBuilder.class);
            UriComponentsBuilder.FullPathComponentBuilder fpBuilder = this.getLastBuilder(UriComponentsBuilder.FullPathComponentBuilder.class);
            if (psBuilder == null) {
               psBuilder = new UriComponentsBuilder.PathSegmentComponentBuilder();
               this.builders.add(psBuilder);
               if (fpBuilder != null) {
                  fpBuilder.removeTrailingSlash();
               }
            }

            psBuilder.append(pathSegments);
         }
      }

      public void addPath(String path) {
         if (StringUtils.hasText(path)) {
            UriComponentsBuilder.PathSegmentComponentBuilder psBuilder = this.getLastBuilder(UriComponentsBuilder.PathSegmentComponentBuilder.class);
            UriComponentsBuilder.FullPathComponentBuilder fpBuilder = this.getLastBuilder(UriComponentsBuilder.FullPathComponentBuilder.class);
            if (psBuilder != null) {
               path = path.startsWith("/") ? path : "/" + path;
            }

            if (fpBuilder == null) {
               fpBuilder = new UriComponentsBuilder.FullPathComponentBuilder();
               this.builders.add(fpBuilder);
            }

            fpBuilder.append(path);
         }
      }

      private <T> @Nullable T getLastBuilder(Class<T> builderClass) {
         if (!this.builders.isEmpty()) {
            UriComponentsBuilder.PathComponentBuilder last = this.builders.getLast();
            if (builderClass.isInstance(last)) {
               return (T)last;
            }
         }

         return null;
      }

      @Override
      public HierarchicalUriComponents.PathComponent build() {
         int size = this.builders.size();
         List<HierarchicalUriComponents.PathComponent> components = new ArrayList<>(size);

         for (UriComponentsBuilder.PathComponentBuilder componentBuilder : this.builders) {
            HierarchicalUriComponents.PathComponent pathComponent = componentBuilder.build();
            if (pathComponent != null) {
               components.add(pathComponent);
            }
         }

         if (components.isEmpty()) {
            return HierarchicalUriComponents.NULL_PATH_COMPONENT;
         } else {
            return components.size() == 1 ? components.get(0) : new HierarchicalUriComponents.PathComponentComposite(components);
         }
      }

      public UriComponentsBuilder.CompositePathComponentBuilder cloneBuilder() {
         UriComponentsBuilder.CompositePathComponentBuilder compositeBuilder = new UriComponentsBuilder.CompositePathComponentBuilder();

         for (UriComponentsBuilder.PathComponentBuilder builder : this.builders) {
            compositeBuilder.builders.add(builder.cloneBuilder());
         }

         return compositeBuilder;
      }
   }

   private enum EncodingHint {
      ENCODE_TEMPLATE,
      FULLY_ENCODED,
      NONE;
   }

   private static class FullPathComponentBuilder implements UriComponentsBuilder.PathComponentBuilder {
      private final StringBuilder path = new StringBuilder();

      public void append(String path) {
         this.path.append(path);
      }

      @Override
      public HierarchicalUriComponents.@Nullable PathComponent build() {
         if (this.path.isEmpty()) {
            return null;
         }

         String sanitized = getSanitizedPath(this.path);
         return new HierarchicalUriComponents.FullPathComponent(sanitized);
      }

      private static String getSanitizedPath(final StringBuilder path) {
         int index = path.indexOf("//");
         if (index < 0) {
            return path.toString();
         }

         StringBuilder sanitized;
         for (sanitized = new StringBuilder(path); index != -1; index = sanitized.indexOf("//", index)) {
            sanitized.deleteCharAt(index);
         }

         return sanitized.toString();
      }

      public void removeTrailingSlash() {
         int index = this.path.length() - 1;
         if (this.path.charAt(index) == '/') {
            this.path.deleteCharAt(index);
         }
      }

      public UriComponentsBuilder.FullPathComponentBuilder cloneBuilder() {
         UriComponentsBuilder.FullPathComponentBuilder builder = new UriComponentsBuilder.FullPathComponentBuilder();
         builder.append(this.path.toString());
         return builder;
      }
   }

   public enum ParserType {
      RFC,
      WHAT_WG;
   }

   private interface PathComponentBuilder {
      HierarchicalUriComponents.@Nullable PathComponent build();

      UriComponentsBuilder.PathComponentBuilder cloneBuilder();
   }

   private static class PathSegmentComponentBuilder implements UriComponentsBuilder.PathComponentBuilder {
      private final List<String> pathSegments = new ArrayList<>();

      public void append(String... pathSegments) {
         for (String pathSegment : pathSegments) {
            if (StringUtils.hasText(pathSegment)) {
               this.pathSegments.add(pathSegment);
            }
         }
      }

      @Override
      public HierarchicalUriComponents.@Nullable PathComponent build() {
         return this.pathSegments.isEmpty() ? null : new HierarchicalUriComponents.PathSegmentComponent(this.pathSegments);
      }

      public UriComponentsBuilder.PathSegmentComponentBuilder cloneBuilder() {
         UriComponentsBuilder.PathSegmentComponentBuilder builder = new UriComponentsBuilder.PathSegmentComponentBuilder();
         builder.pathSegments.addAll(this.pathSegments);
         return builder;
      }
   }
}
