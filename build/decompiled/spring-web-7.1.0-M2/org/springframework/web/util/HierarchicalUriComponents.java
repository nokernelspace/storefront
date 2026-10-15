package org.springframework.web.util;

import java.io.ByteArrayOutputStream;
import java.io.Serializable;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

final class HierarchicalUriComponents extends UriComponents {
   private static final char PATH_DELIMITER = '/';
   private static final String PATH_DELIMITER_STRING = String.valueOf('/');
   private static final MultiValueMap<String, String> EMPTY_QUERY_PARAMS = CollectionUtils.unmodifiableMultiValueMap(new LinkedMultiValueMap());
   static final HierarchicalUriComponents.PathComponent NULL_PATH_COMPONENT = new HierarchicalUriComponents.PathComponent() {
      @Override
      public String getPath() {
         return "";
      }

      @Override
      public List<String> getPathSegments() {
         return Collections.emptyList();
      }

      @Override
      public HierarchicalUriComponents.PathComponent encode(BiFunction<String, HierarchicalUriComponents.Type, String> encoder) {
         return this;
      }

      @Override
      public void verify() {
      }

      @Override
      public HierarchicalUriComponents.PathComponent expand(UriComponents.UriTemplateVariables uriVariables, @Nullable UnaryOperator<String> encoder) {
         return this;
      }

      @Override
      public void copyToUriComponentsBuilder(UriComponentsBuilder builder) {
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other;
      }

      @Override
      public int hashCode() {
         return this.getClass().hashCode();
      }
   };
   private final @Nullable String userInfo;
   private final @Nullable String host;
   private final @Nullable String port;
   private final HierarchicalUriComponents.PathComponent path;
   private final MultiValueMap<String, String> queryParams;
   private final HierarchicalUriComponents.EncodeState encodeState;
   private @Nullable UnaryOperator<String> variableEncoder;

   HierarchicalUriComponents(
      @Nullable String scheme,
      @Nullable String fragment,
      @Nullable String userInfo,
      @Nullable String host,
      @Nullable String port,
      HierarchicalUriComponents.@Nullable PathComponent path,
      @Nullable MultiValueMap<String, String> query,
      boolean encoded
   ) {
      super(scheme, fragment);
      this.userInfo = userInfo;
      this.host = host;
      this.port = port;
      this.path = path != null ? path : NULL_PATH_COMPONENT;
      this.queryParams = query != null ? CollectionUtils.unmodifiableMultiValueMap(query) : EMPTY_QUERY_PARAMS;
      this.encodeState = encoded ? HierarchicalUriComponents.EncodeState.FULLY_ENCODED : HierarchicalUriComponents.EncodeState.RAW;
      if (encoded) {
         this.verify();
      }
   }

   private HierarchicalUriComponents(
      @Nullable String scheme,
      @Nullable String fragment,
      @Nullable String userInfo,
      @Nullable String host,
      @Nullable String port,
      HierarchicalUriComponents.PathComponent path,
      MultiValueMap<String, String> queryParams,
      HierarchicalUriComponents.EncodeState encodeState,
      @Nullable UnaryOperator<String> variableEncoder
   ) {
      super(scheme, fragment);
      this.userInfo = userInfo;
      this.host = host;
      this.port = port;
      this.path = path;
      this.queryParams = queryParams;
      this.encodeState = encodeState;
      this.variableEncoder = variableEncoder;
   }

   @Override
   public @Nullable String getSchemeSpecificPart() {
      return null;
   }

   @Override
   public @Nullable String getUserInfo() {
      return this.userInfo;
   }

   @Override
   public @Nullable String getHost() {
      return this.host;
   }

   @Override
   public int getPort() {
      if (this.port == null) {
         return -1;
      }

      if (this.port.contains("{")) {
         throw new IllegalStateException("The port contains a URI variable but has not been expanded yet: " + this.port);
      }

      try {
         return Integer.parseInt(this.port);
      } catch (NumberFormatException ex) {
         throw new IllegalStateException("The port must be an integer: " + this.port);
      }
   }

   @Override
   public String getPath() {
      return this.path.getPath();
   }

   @Override
   public List<String> getPathSegments() {
      return this.path.getPathSegments();
   }

   @Override
   public @Nullable String getQuery() {
      if (!this.queryParams.isEmpty()) {
         StringBuilder queryBuilder = new StringBuilder();
         this.queryParams.forEach((name, values) -> {
            if (CollectionUtils.isEmpty(values)) {
               if (queryBuilder.length() != 0) {
                  queryBuilder.append('&');
               }

               queryBuilder.append(name);
            } else {
               for (Object value : values) {
                  if (queryBuilder.length() != 0) {
                     queryBuilder.append('&');
                  }

                  queryBuilder.append(name);
                  if (value != null) {
                     queryBuilder.append('=').append(value.toString());
                  }
               }
            }
         });
         return queryBuilder.toString();
      } else {
         return null;
      }
   }

   @Override
   public MultiValueMap<String, String> getQueryParams() {
      return this.queryParams;
   }

   HierarchicalUriComponents encodeTemplate(Charset charset) {
      if (this.encodeState.isEncoded()) {
         return this;
      }

      this.variableEncoder = value -> encodeUriComponent(value, charset, HierarchicalUriComponents.Type.URI);
      HierarchicalUriComponents.UriTemplateEncoder encoder = new HierarchicalUriComponents.UriTemplateEncoder(charset);
      String schemeTo = this.getScheme() != null ? encoder.apply(this.getScheme(), HierarchicalUriComponents.Type.SCHEME) : null;
      String fragmentTo = this.getFragment() != null ? encoder.apply(this.getFragment(), HierarchicalUriComponents.Type.FRAGMENT) : null;
      String userInfoTo = this.getUserInfo() != null ? encoder.apply(this.getUserInfo(), HierarchicalUriComponents.Type.USER_INFO) : null;
      String hostTo = this.getHost() != null ? encoder.apply(this.getHost(), this.getHostType()) : null;
      HierarchicalUriComponents.PathComponent pathTo = this.path.encode(encoder);
      MultiValueMap<String, String> queryParamsTo = this.encodeQueryParams(encoder);
      return new HierarchicalUriComponents(
         schemeTo,
         fragmentTo,
         userInfoTo,
         hostTo,
         this.port,
         pathTo,
         queryParamsTo,
         HierarchicalUriComponents.EncodeState.TEMPLATE_ENCODED,
         this.variableEncoder
      );
   }

   public HierarchicalUriComponents encode(Charset charset) {
      if (this.encodeState.isEncoded()) {
         return this;
      }

      String scheme = this.getScheme();
      String fragment = this.getFragment();
      String schemeTo = scheme != null ? encodeUriComponent(scheme, charset, HierarchicalUriComponents.Type.SCHEME) : null;
      String fragmentTo = fragment != null ? encodeUriComponent(fragment, charset, HierarchicalUriComponents.Type.FRAGMENT) : null;
      String userInfoTo = this.userInfo != null ? encodeUriComponent(this.userInfo, charset, HierarchicalUriComponents.Type.USER_INFO) : null;
      String hostTo = this.host != null ? encodeUriComponent(this.host, charset, this.getHostType()) : null;
      BiFunction<String, HierarchicalUriComponents.Type, String> encoder = (s, type) -> encodeUriComponent(s, charset, type);
      HierarchicalUriComponents.PathComponent pathTo = this.path.encode(encoder);
      MultiValueMap<String, String> queryParamsTo = this.encodeQueryParams(encoder);
      return new HierarchicalUriComponents(
         schemeTo, fragmentTo, userInfoTo, hostTo, this.port, pathTo, queryParamsTo, HierarchicalUriComponents.EncodeState.FULLY_ENCODED, null
      );
   }

   private MultiValueMap<String, String> encodeQueryParams(BiFunction<String, HierarchicalUriComponents.Type, String> encoder) {
      int size = this.queryParams.size();
      MultiValueMap<String, String> result = new LinkedMultiValueMap(size);
      this.queryParams.forEach((key, values) -> {
         String name = encoder.apply(key, HierarchicalUriComponents.Type.QUERY_PARAM);
         List<String> encodedValues = new ArrayList<>(values.size());

         for (String value : values) {
            encodedValues.add(value != null ? encoder.apply(value, HierarchicalUriComponents.Type.QUERY_PARAM) : null);
         }

         result.put(name, encodedValues);
      });
      return CollectionUtils.unmodifiableMultiValueMap(result);
   }

   static String encodeUriComponent(String source, String encoding, HierarchicalUriComponents.Type type) {
      return encodeUriComponent(source, Charset.forName(encoding), type);
   }

   static String encodeUriComponent(String source, Charset charset, HierarchicalUriComponents.Type type) {
      if (!StringUtils.hasLength(source)) {
         return source;
      }

      Assert.notNull(charset, "Charset must not be null");
      Assert.notNull(type, "Type must not be null");
      byte[] bytes = source.getBytes(charset);
      boolean original = true;

      for (byte b : bytes) {
         if (!type.isAllowed(b)) {
            original = false;
            break;
         }
      }

      if (original) {
         return source;
      }

      ByteArrayOutputStream baos = new ByteArrayOutputStream(bytes.length);

      for (byte b : bytes) {
         if (type.isAllowed(b)) {
            baos.write(b);
         } else {
            baos.write(37);
            char hex1 = Character.toUpperCase(Character.forDigit(b >> 4 & 15, 16));
            char hex2 = Character.toUpperCase(Character.forDigit(b & 15, 16));
            baos.write(hex1);
            baos.write(hex2);
         }
      }

      return StreamUtils.copyToString(baos, charset);
   }

   private HierarchicalUriComponents.Type getHostType() {
      return this.host != null && this.host.startsWith("[") ? HierarchicalUriComponents.Type.HOST_IPV6 : HierarchicalUriComponents.Type.HOST_IPV4;
   }

   private void verify() {
      verifyUriComponent(this.getScheme(), HierarchicalUriComponents.Type.SCHEME);
      verifyUriComponent(this.userInfo, HierarchicalUriComponents.Type.USER_INFO);
      verifyUriComponent(this.host, this.getHostType());
      this.path.verify();
      this.queryParams.forEach((key, values) -> {
         verifyUriComponent(key, HierarchicalUriComponents.Type.QUERY_PARAM);

         for (String value : values) {
            verifyUriComponent(value, HierarchicalUriComponents.Type.QUERY_PARAM);
         }
      });
      verifyUriComponent(this.getFragment(), HierarchicalUriComponents.Type.FRAGMENT);
   }

   private static void verifyUriComponent(@Nullable String source, HierarchicalUriComponents.Type type) {
      if (source != null) {
         int length = source.length();

         for (int i = 0; i < length; i++) {
            char ch = source.charAt(i);
            if (ch == '%') {
               if (i + 2 >= length) {
                  throw new IllegalArgumentException("Invalid encoded sequence \"" + source.substring(i) + "\"");
               }

               char hex1 = source.charAt(i + 1);
               char hex2 = source.charAt(i + 2);
               int u = Character.digit(hex1, 16);
               int l = Character.digit(hex2, 16);
               if (u == -1 || l == -1) {
                  throw new IllegalArgumentException("Invalid encoded sequence \"" + source.substring(i) + "\"");
               }

               i += 2;
            } else if (!type.isAllowed(ch)) {
               throw new IllegalArgumentException("Invalid character '" + ch + "' for " + type.name() + " in \"" + source + "\"");
            }
         }
      }
   }

   protected HierarchicalUriComponents expandInternal(UriComponents.UriTemplateVariables uriVariables) {
      Assert.state(
         !this.encodeState.equals(HierarchicalUriComponents.EncodeState.FULLY_ENCODED),
         "URI components already encoded, and could not possibly contain '{' or '}'."
      );
      String schemeTo = expandUriComponent(this.getScheme(), uriVariables, this.variableEncoder);
      String userInfoTo = expandUriComponent(this.userInfo, uriVariables, this.variableEncoder);
      String hostTo = expandUriComponent(this.host, uriVariables, this.variableEncoder);
      String portTo = expandUriComponent(this.port, uriVariables, this.variableEncoder);
      HierarchicalUriComponents.PathComponent pathTo = this.path.expand(uriVariables, this.variableEncoder);
      MultiValueMap<String, String> queryParamsTo = this.expandQueryParams(uriVariables);
      String fragmentTo = expandUriComponent(this.getFragment(), uriVariables, this.variableEncoder);
      return new HierarchicalUriComponents(schemeTo, fragmentTo, userInfoTo, hostTo, portTo, pathTo, queryParamsTo, this.encodeState, this.variableEncoder);
   }

   private MultiValueMap<String, String> expandQueryParams(UriComponents.UriTemplateVariables variables) {
      int size = this.queryParams.size();
      MultiValueMap<String, String> result = new LinkedMultiValueMap(size);
      UriComponents.UriTemplateVariables queryVariables = new HierarchicalUriComponents.QueryUriTemplateVariables(variables);
      this.queryParams.forEach((key, values) -> {
         String name = expandUriComponent(key, queryVariables, this.variableEncoder);
         List<String> expandedValues = (List<String>)result.computeIfAbsent(name, k -> new ArrayList(values.size()));

         for (String value : values) {
            expandedValues.add(expandUriComponent(value, queryVariables, this.variableEncoder));
         }
      });
      return CollectionUtils.unmodifiableMultiValueMap(result);
   }

   @Override
   public UriComponents normalize() {
      String normalizedPath = StringUtils.cleanPath(this.getPath());
      HierarchicalUriComponents.FullPathComponent path = new HierarchicalUriComponents.FullPathComponent(normalizedPath);
      return new HierarchicalUriComponents(
         this.getScheme(), this.getFragment(), this.userInfo, this.host, this.port, path, this.queryParams, this.encodeState, this.variableEncoder
      );
   }

   @Override
   public String toUriString() {
      StringBuilder uriBuilder = new StringBuilder();
      if (this.getScheme() != null) {
         uriBuilder.append(this.getScheme()).append(':');
      }

      if (this.userInfo != null || this.host != null) {
         uriBuilder.append("//");
         if (this.userInfo != null) {
            uriBuilder.append(this.userInfo).append('@');
         }

         if (this.host != null) {
            uriBuilder.append(this.host);
         }

         if (StringUtils.hasText(this.port) && !this.port.equals("-1")) {
            uriBuilder.append(':').append(this.port);
         }
      }

      String path = this.getPath();
      if (StringUtils.hasLength(path)) {
         if (uriBuilder.length() != 0 && path.charAt(0) != '/') {
            uriBuilder.append('/');
         }

         uriBuilder.append(path);
      }

      String query = this.getQuery();
      if (query != null) {
         uriBuilder.append('?').append(query);
      }

      if (this.getFragment() != null) {
         uriBuilder.append('#').append(this.getFragment());
      }

      return uriBuilder.toString();
   }

   @Override
   public URI toUri() {
      try {
         if (this.encodeState.isEncoded()) {
            return new URI(this.toUriString());
         }

         String path = this.getPath();
         if (StringUtils.hasLength(path)
            && path.charAt(0) != '/'
            && (this.getScheme() != null || this.getUserInfo() != null || this.getHost() != null || this.getPort() != -1)) {
            path = "/" + path;
         }

         return new URI(this.getScheme(), this.getUserInfo(), this.getHost(), this.getPort(), path, this.getQuery(), this.getFragment());
      } catch (URISyntaxException ex) {
         throw new IllegalStateException("Could not create URI object: " + ex.getMessage(), ex);
      }
   }

   @Override
   protected void copyToUriComponentsBuilder(UriComponentsBuilder builder) {
      if (this.getScheme() != null) {
         builder.scheme(this.getScheme());
      }

      if (this.getUserInfo() != null) {
         builder.userInfo(this.getUserInfo());
      }

      if (this.getHost() != null) {
         builder.host(this.getHost());
      }

      if (this.port != null) {
         builder.port(this.port);
      }

      this.path.copyToUriComponentsBuilder(builder);
      if (!this.getQueryParams().isEmpty()) {
         builder.queryParams(this.getQueryParams());
      }

      if (this.getFragment() != null) {
         builder.fragment(this.getFragment());
      }
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof HierarchicalUriComponents that
            && ObjectUtils.nullSafeEquals(this.getScheme(), that.getScheme())
            && ObjectUtils.nullSafeEquals(this.getUserInfo(), that.getUserInfo())
            && ObjectUtils.nullSafeEquals(this.getHost(), that.getHost())
            && this.getPort() == that.getPort()
            && this.path.equals(that.path)
            && this.queryParams.equals(that.queryParams)
            && ObjectUtils.nullSafeEquals(this.getFragment(), that.getFragment());
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.getScheme(), this.userInfo, this.host, this.port, this.path, this.queryParams, this.getFragment());
   }

   private enum EncodeState {
      RAW,
      FULLY_ENCODED,
      TEMPLATE_ENCODED;

      public boolean isEncoded() {
         return this.equals(FULLY_ENCODED) || this.equals(TEMPLATE_ENCODED);
      }
   }

   static final class FullPathComponent implements HierarchicalUriComponents.PathComponent {
      private final String path;

      public FullPathComponent(@Nullable String path) {
         this.path = path != null ? path : "";
      }

      @Override
      public String getPath() {
         return this.path;
      }

      @Override
      public List<String> getPathSegments() {
         String[] segments = StringUtils.tokenizeToStringArray(this.getPath(), HierarchicalUriComponents.PATH_DELIMITER_STRING);
         return List.of(segments);
      }

      @Override
      public HierarchicalUriComponents.PathComponent encode(BiFunction<String, HierarchicalUriComponents.Type, String> encoder) {
         String encodedPath = encoder.apply(this.getPath(), HierarchicalUriComponents.Type.PATH);
         return new HierarchicalUriComponents.FullPathComponent(encodedPath);
      }

      @Override
      public void verify() {
         HierarchicalUriComponents.verifyUriComponent(this.getPath(), HierarchicalUriComponents.Type.PATH);
      }

      @Override
      public HierarchicalUriComponents.PathComponent expand(UriComponents.UriTemplateVariables uriVariables, @Nullable UnaryOperator<String> encoder) {
         String expandedPath = UriComponents.expandUriComponent(this.getPath(), uriVariables, encoder);
         return new HierarchicalUriComponents.FullPathComponent(expandedPath);
      }

      @Override
      public void copyToUriComponentsBuilder(UriComponentsBuilder builder) {
         builder.path(this.getPath());
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other
            || other instanceof HierarchicalUriComponents.FullPathComponent fullPathComponent && this.getPath().equals(fullPathComponent.getPath());
      }

      @Override
      public int hashCode() {
         return this.getPath().hashCode();
      }
   }

   interface PathComponent extends Serializable {
      String getPath();

      List<String> getPathSegments();

      HierarchicalUriComponents.PathComponent encode(BiFunction<String, HierarchicalUriComponents.Type, String> encoder);

      void verify();

      HierarchicalUriComponents.PathComponent expand(UriComponents.UriTemplateVariables uriVariables, @Nullable UnaryOperator<String> encoder);

      void copyToUriComponentsBuilder(UriComponentsBuilder builder);
   }

   static final class PathComponentComposite implements HierarchicalUriComponents.PathComponent {
      private final List<HierarchicalUriComponents.PathComponent> pathComponents;

      public PathComponentComposite(List<HierarchicalUriComponents.PathComponent> pathComponents) {
         Assert.notNull(pathComponents, "PathComponent List must not be null");
         this.pathComponents = pathComponents;
      }

      @Override
      public String getPath() {
         StringBuilder pathBuilder = new StringBuilder();

         for (HierarchicalUriComponents.PathComponent pathComponent : this.pathComponents) {
            pathBuilder.append(pathComponent.getPath());
         }

         return pathBuilder.toString();
      }

      @Override
      public List<String> getPathSegments() {
         List<String> result = new ArrayList<>();

         for (HierarchicalUriComponents.PathComponent pathComponent : this.pathComponents) {
            result.addAll(pathComponent.getPathSegments());
         }

         return result;
      }

      @Override
      public HierarchicalUriComponents.PathComponent encode(BiFunction<String, HierarchicalUriComponents.Type, String> encoder) {
         List<HierarchicalUriComponents.PathComponent> encodedComponents = new ArrayList<>(this.pathComponents.size());

         for (HierarchicalUriComponents.PathComponent pathComponent : this.pathComponents) {
            encodedComponents.add(pathComponent.encode(encoder));
         }

         return new HierarchicalUriComponents.PathComponentComposite(encodedComponents);
      }

      @Override
      public void verify() {
         for (HierarchicalUriComponents.PathComponent pathComponent : this.pathComponents) {
            pathComponent.verify();
         }
      }

      @Override
      public HierarchicalUriComponents.PathComponent expand(UriComponents.UriTemplateVariables uriVariables, @Nullable UnaryOperator<String> encoder) {
         List<HierarchicalUriComponents.PathComponent> expandedComponents = new ArrayList<>(this.pathComponents.size());

         for (HierarchicalUriComponents.PathComponent pathComponent : this.pathComponents) {
            expandedComponents.add(pathComponent.expand(uriVariables, encoder));
         }

         return new HierarchicalUriComponents.PathComponentComposite(expandedComponents);
      }

      @Override
      public void copyToUriComponentsBuilder(UriComponentsBuilder builder) {
         for (HierarchicalUriComponents.PathComponent pathComponent : this.pathComponents) {
            pathComponent.copyToUriComponentsBuilder(builder);
         }
      }
   }

   static final class PathSegmentComponent implements HierarchicalUriComponents.PathComponent {
      private final List<String> pathSegments;

      public PathSegmentComponent(List<String> pathSegments) {
         Assert.notNull(pathSegments, "List must not be null");
         this.pathSegments = List.copyOf(pathSegments);
      }

      @Override
      public String getPath() {
         String delimiter = HierarchicalUriComponents.PATH_DELIMITER_STRING;
         StringJoiner pathBuilder = new StringJoiner(delimiter, delimiter, "");

         for (String pathSegment : this.pathSegments) {
            pathBuilder.add(pathSegment);
         }

         return pathBuilder.toString();
      }

      @Override
      public List<String> getPathSegments() {
         return this.pathSegments;
      }

      @Override
      public HierarchicalUriComponents.PathComponent encode(BiFunction<String, HierarchicalUriComponents.Type, String> encoder) {
         List<String> pathSegments = this.getPathSegments();
         List<String> encodedPathSegments = new ArrayList<>(pathSegments.size());

         for (String pathSegment : pathSegments) {
            String encodedPathSegment = encoder.apply(pathSegment, HierarchicalUriComponents.Type.PATH_SEGMENT);
            encodedPathSegments.add(encodedPathSegment);
         }

         return new HierarchicalUriComponents.PathSegmentComponent(encodedPathSegments);
      }

      @Override
      public void verify() {
         for (String pathSegment : this.getPathSegments()) {
            HierarchicalUriComponents.verifyUriComponent(pathSegment, HierarchicalUriComponents.Type.PATH_SEGMENT);
         }
      }

      @Override
      public HierarchicalUriComponents.PathComponent expand(UriComponents.UriTemplateVariables uriVariables, @Nullable UnaryOperator<String> encoder) {
         List<String> pathSegments = this.getPathSegments();
         List<String> expandedPathSegments = new ArrayList<>(pathSegments.size());

         for (String pathSegment : pathSegments) {
            String expandedPathSegment = UriComponents.expandUriComponent(pathSegment, uriVariables, encoder);
            expandedPathSegments.add(expandedPathSegment);
         }

         return new HierarchicalUriComponents.PathSegmentComponent(expandedPathSegments);
      }

      @Override
      public void copyToUriComponentsBuilder(UriComponentsBuilder builder) {
         builder.pathSegment(StringUtils.toStringArray(this.getPathSegments()));
      }

      @Override
      public boolean equals(@Nullable Object other) {
         return this == other
            || other instanceof HierarchicalUriComponents.PathSegmentComponent pathSegmentComponent
               && this.getPathSegments().equals(pathSegmentComponent.getPathSegments());
      }

      @Override
      public int hashCode() {
         return this.getPathSegments().hashCode();
      }
   }

   private static class QueryUriTemplateVariables implements UriComponents.UriTemplateVariables {
      private final UriComponents.UriTemplateVariables delegate;

      public QueryUriTemplateVariables(UriComponents.UriTemplateVariables delegate) {
         this.delegate = delegate;
      }

      @Override
      public @Nullable Object getValue(@Nullable String name) {
         Object value = this.delegate.getValue(name);
         if (ObjectUtils.isArray(value)) {
            value = StringUtils.arrayToCommaDelimitedString(ObjectUtils.toObjectArray(value));
         } else if (value instanceof Collection<?> collection) {
            value = StringUtils.collectionToCommaDelimitedString(collection);
         }

         return value;
      }
   }

   enum Type {
      SCHEME {
         @Override
         public boolean isAllowed(int c) {
            return this.isAlpha(c) || this.isDigit(c) || 43 == c || 45 == c || 46 == c;
         }
      },
      AUTHORITY {
         @Override
         public boolean isAllowed(int c) {
            return this.isUnreservedOrSubDelimiter(c) || 58 == c || 64 == c;
         }
      },
      USER_INFO {
         @Override
         public boolean isAllowed(int c) {
            return this.isUnreservedOrSubDelimiter(c) || 58 == c;
         }
      },
      HOST_IPV4 {
         @Override
         public boolean isAllowed(int c) {
            return this.isUnreservedOrSubDelimiter(c);
         }
      },
      HOST_IPV6 {
         @Override
         public boolean isAllowed(int c) {
            return this.isUnreservedOrSubDelimiter(c) || 91 == c || 93 == c || 58 == c;
         }
      },
      PORT {
         @Override
         public boolean isAllowed(int c) {
            return this.isDigit(c);
         }
      },
      PATH {
         @Override
         public boolean isAllowed(int c) {
            return this.isPchar(c) || 47 == c;
         }
      },
      PATH_SEGMENT {
         @Override
         public boolean isAllowed(int c) {
            return this.isPchar(c);
         }
      },
      QUERY {
         @Override
         public boolean isAllowed(int c) {
            return this.isPchar(c) || 47 == c || 63 == c;
         }
      },
      QUERY_PARAM {
         @Override
         public boolean isAllowed(int c) {
            return 61 != c && 38 != c ? this.isPchar(c) || 47 == c || 63 == c : false;
         }
      },
      FRAGMENT {
         @Override
         public boolean isAllowed(int c) {
            return this.isPchar(c) || 47 == c || 63 == c;
         }
      },
      URI {
         @Override
         public boolean isAllowed(int c) {
            return this.isUnreserved(c);
         }
      };

      private static final boolean[] unreservedOrSubDelimiterArray = new boolean[128];

      public abstract boolean isAllowed(int c);

      protected boolean isAlpha(int c) {
         return c >= 97 && c <= 122 || c >= 65 && c <= 90;
      }

      protected boolean isDigit(int c) {
         return c >= 48 && c <= 57;
      }

      protected boolean isGenericDelimiter(int c) {
         return 58 == c || 47 == c || 63 == c || 35 == c || 91 == c || 93 == c || 64 == c;
      }

      protected boolean isSubDelimiter(int c) {
         return 33 == c || 36 == c || 38 == c || 39 == c || 40 == c || 41 == c || 42 == c || 43 == c || 44 == c || 59 == c || 61 == c;
      }

      protected boolean isReserved(int c) {
         return this.isGenericDelimiter(c) || this.isSubDelimiter(c);
      }

      protected boolean isUnreserved(int c) {
         return this.isAlpha(c) || this.isDigit(c) || 45 == c || 46 == c || 95 == c || 126 == c;
      }

      protected boolean isPchar(int c) {
         return this.isUnreservedOrSubDelimiter(c) || 58 == c || 64 == c;
      }

      protected boolean isUnreservedOrSubDelimiter(int c) {
         return c < unreservedOrSubDelimiterArray.length && c >= 0 && unreservedOrSubDelimiterArray[c];
      }

      static {
         for (int i = 0; i < 128; i++) {
            char c = (char)i;
            unreservedOrSubDelimiterArray[i] = URI.isUnreserved(c) || URI.isSubDelimiter(c);
         }
      }
   }

   private static class UriTemplateEncoder implements BiFunction<String, HierarchicalUriComponents.Type, String> {
      private final Charset charset;
      private final StringBuilder currentLiteral = new StringBuilder();
      private final StringBuilder currentVariable = new StringBuilder();
      private final StringBuilder output = new StringBuilder();
      private boolean variableWithNameAndRegex;

      public UriTemplateEncoder(Charset charset) {
         this.charset = charset;
      }

      public String apply(String source, HierarchicalUriComponents.Type type) {
         if (this.isUriVariable(source)) {
            return source;
         }

         if (source.indexOf(123) == -1) {
            return HierarchicalUriComponents.encodeUriComponent(source, this.charset, type);
         }

         int level = 0;
         this.clear(this.currentLiteral);
         this.clear(this.currentVariable);
         this.clear(this.output);

         for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == ':' && level == 1) {
               this.variableWithNameAndRegex = true;
            }

            if (c == '{') {
               if (++level == 1) {
                  this.append(this.currentLiteral, true, type);
               }
            }

            if (c == '}' && level > 0) {
               level--;
               this.currentVariable.append('}');
               if (level == 0) {
                  boolean encode = !this.isUriVariable(this.currentVariable);
                  this.append(this.currentVariable, encode, type);
               } else if (!this.variableWithNameAndRegex) {
                  this.append(this.currentVariable, true, type);
                  level = 0;
               }
            } else if (level > 0) {
               this.currentVariable.append(c);
            } else {
               this.currentLiteral.append(c);
            }
         }

         if (level > 0) {
            this.currentLiteral.append(this.currentVariable);
         }

         this.append(this.currentLiteral, true, type);
         return this.output.toString();
      }

      private boolean isUriVariable(CharSequence source) {
         if (source.length() >= 2 && source.charAt(0) == '{' && source.charAt(source.length() - 1) == '}') {
            boolean hasText = false;

            for (int i = 1; i < source.length() - 1; i++) {
               char c = source.charAt(i);
               if (c == ':' && i > 1) {
                  return true;
               }

               if (c == '{' || c == '}') {
                  return false;
               }

               hasText = hasText || !Character.isWhitespace(c);
            }

            return hasText;
         } else {
            return false;
         }
      }

      private void append(StringBuilder sb, boolean encode, HierarchicalUriComponents.Type type) {
         this.output.append(encode ? HierarchicalUriComponents.encodeUriComponent(sb.toString(), this.charset, type) : sb);
         this.clear(sb);
         this.variableWithNameAndRegex = false;
      }

      private void clear(StringBuilder sb) {
         sb.delete(0, sb.length());
      }
   }
}
