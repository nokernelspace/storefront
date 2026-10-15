package org.springframework.web.util;

import java.net.URI;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

public class DefaultUriBuilderFactory implements UriBuilderFactory {
   private final @Nullable UriComponentsBuilder baseUri;
   private UriComponentsBuilder.@Nullable ParserType parserType;
   private DefaultUriBuilderFactory.EncodingMode encodingMode = DefaultUriBuilderFactory.EncodingMode.TEMPLATE_AND_VALUES;
   private @Nullable Map<String, @Nullable Object> defaultUriVariables;
   private boolean parsePath = true;

   public DefaultUriBuilderFactory() {
      this.baseUri = null;
   }

   public DefaultUriBuilderFactory(String baseUriTemplate) {
      this.baseUri = UriComponentsBuilder.fromUriString(baseUriTemplate);
   }

   public DefaultUriBuilderFactory(UriComponentsBuilder baseUri) {
      this.baseUri = baseUri;
   }

   public final boolean hasBaseUri() {
      return this.baseUri != null;
   }

   public void setParserType(UriComponentsBuilder.ParserType parserType) {
      this.parserType = parserType;
   }

   public UriComponentsBuilder.@Nullable ParserType getParserType() {
      return this.parserType;
   }

   public void setEncodingMode(DefaultUriBuilderFactory.EncodingMode encodingMode) {
      this.encodingMode = encodingMode;
   }

   public DefaultUriBuilderFactory.EncodingMode getEncodingMode() {
      return this.encodingMode;
   }

   public void setDefaultUriVariables(@Nullable Map<String, ? extends @Nullable Object> defaultUriVariables) {
      if (defaultUriVariables != null) {
         if (this.defaultUriVariables == null) {
            this.defaultUriVariables = new HashMap<>(defaultUriVariables);
         } else {
            this.defaultUriVariables.putAll(defaultUriVariables);
         }
      } else if (this.defaultUriVariables != null) {
         this.defaultUriVariables.clear();
      }
   }

   public Map<String, ?> getDefaultUriVariables() {
      return this.defaultUriVariables != null ? Collections.unmodifiableMap(this.defaultUriVariables) : Collections.emptyMap();
   }

   public void setParsePath(boolean parsePath) {
      this.parsePath = parsePath;
   }

   public boolean shouldParsePath() {
      return this.parsePath;
   }

   @Override
   public URI expand(String uriTemplate, Map<String, ? extends @Nullable Object> uriVars) {
      return this.uriString(uriTemplate).build(uriVars);
   }

   @Override
   public URI expand(String uriTemplate, @Nullable Object... uriVars) {
      return this.uriString(uriTemplate).build(uriVars);
   }

   @Override
   public UriBuilder uriString(String uriTemplate) {
      return new DefaultUriBuilderFactory.DefaultUriBuilder(uriTemplate);
   }

   @Override
   public UriBuilder builder() {
      return new DefaultUriBuilderFactory.DefaultUriBuilder("");
   }

   private class DefaultUriBuilder implements UriBuilder {
      private final UriComponentsBuilder uriComponentsBuilder;

      public DefaultUriBuilder(String uriTemplate) {
         this.uriComponentsBuilder = this.initUriComponentsBuilder(uriTemplate);
      }

      private UriComponentsBuilder initUriComponentsBuilder(String uriTemplate) {
         UriComponentsBuilder result;
         if (!StringUtils.hasLength(uriTemplate)) {
            result = DefaultUriBuilderFactory.this.baseUri != null ? DefaultUriBuilderFactory.this.baseUri.cloneBuilder() : UriComponentsBuilder.newInstance();
         } else if (DefaultUriBuilderFactory.this.baseUri != null) {
            UriComponentsBuilder builder = this.parseUri(uriTemplate);
            UriComponents uri = builder.build();
            result = uri.getHost() == null ? DefaultUriBuilderFactory.this.baseUri.cloneBuilder().uriComponents(uri) : builder;
         } else {
            result = this.parseUri(uriTemplate);
         }

         if (DefaultUriBuilderFactory.this.encodingMode.equals(DefaultUriBuilderFactory.EncodingMode.TEMPLATE_AND_VALUES)) {
            result.encode();
         }

         this.parsePathIfNecessary(result);
         return result;
      }

      private UriComponentsBuilder parseUri(String uriTemplate) {
         return DefaultUriBuilderFactory.this.getParserType() != null
            ? UriComponentsBuilder.fromUriString(uriTemplate, DefaultUriBuilderFactory.this.getParserType())
            : UriComponentsBuilder.fromUriString(uriTemplate);
      }

      private void parsePathIfNecessary(UriComponentsBuilder result) {
         if (DefaultUriBuilderFactory.this.parsePath && DefaultUriBuilderFactory.this.encodingMode.equals(DefaultUriBuilderFactory.EncodingMode.URI_COMPONENT)) {
            UriComponents uric = result.build();
            String path = uric.getPath();
            result.replacePath(null);

            for (String segment : uric.getPathSegments()) {
               result.pathSegment(segment);
            }

            if (path != null && path.endsWith("/")) {
               result.path("/");
            }
         }
      }

      public DefaultUriBuilderFactory.DefaultUriBuilder scheme(@Nullable String scheme) {
         this.uriComponentsBuilder.scheme(scheme);
         return this;
      }

      public DefaultUriBuilderFactory.DefaultUriBuilder userInfo(@Nullable String userInfo) {
         this.uriComponentsBuilder.userInfo(userInfo);
         return this;
      }

      public DefaultUriBuilderFactory.DefaultUriBuilder host(@Nullable String host) {
         this.uriComponentsBuilder.host(host);
         return this;
      }

      public DefaultUriBuilderFactory.DefaultUriBuilder port(int port) {
         this.uriComponentsBuilder.port(port);
         return this;
      }

      public DefaultUriBuilderFactory.DefaultUriBuilder port(@Nullable String port) {
         this.uriComponentsBuilder.port(port);
         return this;
      }

      public DefaultUriBuilderFactory.DefaultUriBuilder path(String path) {
         this.uriComponentsBuilder.path(path);
         return this;
      }

      public DefaultUriBuilderFactory.DefaultUriBuilder replacePath(@Nullable String path) {
         this.uriComponentsBuilder.replacePath(path);
         return this;
      }

      public DefaultUriBuilderFactory.DefaultUriBuilder pathSegment(String... pathSegments) {
         this.uriComponentsBuilder.pathSegment(pathSegments);
         return this;
      }

      public DefaultUriBuilderFactory.DefaultUriBuilder query(String query) {
         this.uriComponentsBuilder.query(query);
         return this;
      }

      public DefaultUriBuilderFactory.DefaultUriBuilder replaceQuery(@Nullable String query) {
         this.uriComponentsBuilder.replaceQuery(query);
         return this;
      }

      public DefaultUriBuilderFactory.DefaultUriBuilder queryParam(String name, Object... values) {
         this.uriComponentsBuilder.queryParam(name, values);
         return this;
      }

      public DefaultUriBuilderFactory.DefaultUriBuilder queryParam(String name, @Nullable Collection<?> values) {
         this.uriComponentsBuilder.queryParam(name, values);
         return this;
      }

      public DefaultUriBuilderFactory.DefaultUriBuilder queryParamIfPresent(String name, Optional<?> value) {
         this.uriComponentsBuilder.queryParamIfPresent(name, value);
         return this;
      }

      public DefaultUriBuilderFactory.DefaultUriBuilder queryParams(MultiValueMap<String, String> params) {
         this.uriComponentsBuilder.queryParams(params);
         return this;
      }

      public DefaultUriBuilderFactory.DefaultUriBuilder replaceQueryParam(String name, Object... values) {
         this.uriComponentsBuilder.replaceQueryParam(name, values);
         return this;
      }

      public DefaultUriBuilderFactory.DefaultUriBuilder replaceQueryParam(String name, @Nullable Collection<?> values) {
         this.uriComponentsBuilder.replaceQueryParam(name, values);
         return this;
      }

      public DefaultUriBuilderFactory.DefaultUriBuilder replaceQueryParams(MultiValueMap<String, String> params) {
         this.uriComponentsBuilder.replaceQueryParams(params);
         return this;
      }

      public DefaultUriBuilderFactory.DefaultUriBuilder fragment(@Nullable String fragment) {
         this.uriComponentsBuilder.fragment(fragment);
         return this;
      }

      @Override
      public URI build(Map<String, ?> uriVars) {
         if (!CollectionUtils.isEmpty(DefaultUriBuilderFactory.this.defaultUriVariables)) {
            Map<String, Object> map = CollectionUtils.newHashMap(DefaultUriBuilderFactory.this.defaultUriVariables.size() + uriVars.size());
            map.putAll(DefaultUriBuilderFactory.this.defaultUriVariables);
            map.putAll((Map<? extends String, ? extends Object>)uriVars);
            uriVars = map;
         }

         if (DefaultUriBuilderFactory.this.encodingMode.equals(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY)) {
            uriVars = UriUtils.encodeUriVariables((Map<String, ? extends Object>)uriVars);
         }

         UriComponents uric = this.uriComponentsBuilder.build().expand((Map<String, ? extends Object>)uriVars);
         return this.createUri(uric);
      }

      @Override
      public URI build(@Nullable Object... uriVars) {
         if (ObjectUtils.isEmpty(uriVars) && !CollectionUtils.isEmpty(DefaultUriBuilderFactory.this.defaultUriVariables)) {
            return this.build(Collections.emptyMap());
         }

         if (DefaultUriBuilderFactory.this.encodingMode.equals(DefaultUriBuilderFactory.EncodingMode.VALUES_ONLY)) {
            uriVars = UriUtils.encodeUriVariables(uriVars);
         }

         UriComponents uric = this.uriComponentsBuilder.build().expand(uriVars);
         return this.createUri(uric);
      }

      private URI createUri(UriComponents uric) {
         if (DefaultUriBuilderFactory.this.encodingMode.equals(DefaultUriBuilderFactory.EncodingMode.URI_COMPONENT)) {
            uric = uric.encode();
         }

         return URI.create(uric.toString());
      }

      @Override
      public String toUriString() {
         return this.uriComponentsBuilder.build().toUriString();
      }
   }

   public enum EncodingMode {
      TEMPLATE_AND_VALUES,
      VALUES_ONLY,
      URI_COMPONENT,
      NONE;
   }
}
