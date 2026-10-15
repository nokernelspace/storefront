package org.springframework.web.util;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;

final class OpaqueUriComponents extends UriComponents {
   private static final MultiValueMap<String, String> QUERY_PARAMS_NONE = new LinkedMultiValueMap();
   private final @Nullable String ssp;

   OpaqueUriComponents(@Nullable String scheme, @Nullable String schemeSpecificPart, @Nullable String fragment) {
      super(scheme, fragment);
      this.ssp = schemeSpecificPart;
   }

   @Override
   public @Nullable String getSchemeSpecificPart() {
      return this.ssp;
   }

   @Override
   public @Nullable String getUserInfo() {
      return null;
   }

   @Override
   public @Nullable String getHost() {
      return null;
   }

   @Override
   public int getPort() {
      return -1;
   }

   @Override
   public @Nullable String getPath() {
      return null;
   }

   @Override
   public List<String> getPathSegments() {
      return Collections.emptyList();
   }

   @Override
   public @Nullable String getQuery() {
      return null;
   }

   @Override
   public MultiValueMap<String, String> getQueryParams() {
      return QUERY_PARAMS_NONE;
   }

   @Override
   public UriComponents encode(Charset charset) {
      return this;
   }

   @Override
   protected UriComponents expandInternal(UriComponents.UriTemplateVariables uriVariables) {
      String expandedScheme = expandUriComponent(this.getScheme(), uriVariables);
      String expandedSsp = expandUriComponent(this.getSchemeSpecificPart(), uriVariables);
      String expandedFragment = expandUriComponent(this.getFragment(), uriVariables);
      return new OpaqueUriComponents(expandedScheme, expandedSsp, expandedFragment);
   }

   @Override
   public UriComponents normalize() {
      return this;
   }

   @Override
   public String toUriString() {
      StringBuilder uriBuilder = new StringBuilder();
      if (this.getScheme() != null) {
         uriBuilder.append(this.getScheme());
         uriBuilder.append(':');
      }

      if (this.ssp != null) {
         uriBuilder.append(this.ssp);
      }

      if (this.getFragment() != null) {
         uriBuilder.append('#');
         uriBuilder.append(this.getFragment());
      }

      return uriBuilder.toString();
   }

   @Override
   public URI toUri() {
      try {
         return new URI(this.getScheme(), this.ssp, this.getFragment());
      } catch (URISyntaxException ex) {
         throw new IllegalStateException("Could not create URI object: " + ex.getMessage(), ex);
      }
   }

   @Override
   protected void copyToUriComponentsBuilder(UriComponentsBuilder builder) {
      if (this.getScheme() != null) {
         builder.scheme(this.getScheme());
      }

      if (this.getSchemeSpecificPart() != null) {
         builder.schemeSpecificPart(this.getSchemeSpecificPart());
      }

      if (this.getFragment() != null) {
         builder.fragment(this.getFragment());
      }
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other
         || other instanceof OpaqueUriComponents that
            && ObjectUtils.nullSafeEquals(this.getScheme(), that.getScheme())
            && ObjectUtils.nullSafeEquals(this.ssp, that.ssp)
            && ObjectUtils.nullSafeEquals(this.getFragment(), that.getFragment());
   }

   @Override
   public int hashCode() {
      return Objects.hash(this.getScheme(), this.ssp, this.getFragment());
   }
}
