package org.springframework.web.client;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.Assert;
import org.springframework.web.util.UriComponentsBuilder;

final class DefaultApiVersionInserter implements ApiVersionInserter {
   private final @Nullable String header;
   private final @Nullable String queryParam;
   private final @Nullable String mediaTypeParam;
   private final @Nullable Integer pathSegmentIndex;
   private final ApiVersionFormatter versionFormatter;

   DefaultApiVersionInserter(
      @Nullable String header,
      @Nullable String queryParam,
      @Nullable String mediaTypeParam,
      @Nullable Integer pathSegmentIndex,
      @Nullable ApiVersionFormatter formatter
   ) {
      Assert.isTrue(
         header != null || queryParam != null || mediaTypeParam != null || pathSegmentIndex != null,
         "Expected 'header', 'queryParam', 'mediaTypeParam', or 'pathSegmentIndex' to be configured"
      );
      this.header = header;
      this.queryParam = queryParam;
      this.mediaTypeParam = mediaTypeParam;
      this.pathSegmentIndex = pathSegmentIndex;
      this.versionFormatter = formatter != null ? formatter : Object::toString;
   }

   @Override
   public URI insertVersion(Object version, URI uri) {
      if (this.queryParam == null && this.pathSegmentIndex == null) {
         return uri;
      }

      String formattedVersion = this.versionFormatter.formatVersion(version);
      UriComponentsBuilder builder = UriComponentsBuilder.fromUri(uri);
      if (this.queryParam != null) {
         builder.queryParam(this.queryParam, formattedVersion);
      }

      if (this.pathSegmentIndex != null) {
         List<String> pathSegments = new ArrayList<>(builder.build().getPathSegments());
         this.assertPathSegmentIndex(this.pathSegmentIndex, pathSegments.size(), uri);
         pathSegments.add(this.pathSegmentIndex, formattedVersion);
         builder.replacePath(null);
         UriComponentsBuilder var6 = builder;
         pathSegments.forEach(xva$0 -> var6.pathSegment(xva$0));
      }

      return builder.build(true).toUri();
   }

   private void assertPathSegmentIndex(Integer index, int pathSegmentsSize, URI uri) {
      Assert.state(index <= pathSegmentsSize, "Cannot insert version into '" + uri.getPath() + "' at path segment index " + index);
   }

   @Override
   public void insertVersion(Object version, HttpHeaders headers) {
      if (this.header != null) {
         String formattedVersion = this.versionFormatter.formatVersion(version);
         headers.set(this.header, formattedVersion);
      }

      if (this.mediaTypeParam != null) {
         MediaType contentType = headers.getContentType();
         if (contentType != null) {
            Map<String, String> params = new LinkedHashMap<>(contentType.getParameters());
            params.put(this.mediaTypeParam, this.versionFormatter.formatVersion(version));
            contentType = new MediaType(contentType, params);
            headers.setContentType(contentType);
         }
      }
   }
}
