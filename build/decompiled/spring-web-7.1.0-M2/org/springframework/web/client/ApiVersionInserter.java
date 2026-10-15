package org.springframework.web.client;

import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;

public interface ApiVersionInserter {
   default URI insertVersion(Object version, URI uri) {
      return uri;
   }

   default void insertVersion(Object version, HttpHeaders headers) {
   }

   static ApiVersionInserter useHeader(@Nullable String header) {
      return new DefaultApiVersionInserterBuilder(header, null, null, null).build();
   }

   static ApiVersionInserter useQueryParam(@Nullable String queryParam) {
      return new DefaultApiVersionInserterBuilder(null, queryParam, null, null).build();
   }

   static ApiVersionInserter useMediaTypeParam(@Nullable String mediaTypeParam) {
      return new DefaultApiVersionInserterBuilder(null, null, mediaTypeParam, null).build();
   }

   static ApiVersionInserter usePathSegment(@Nullable Integer pathSegmentIndex) {
      return new DefaultApiVersionInserterBuilder(null, null, null, pathSegmentIndex).build();
   }

   static ApiVersionInserter.Builder builder() {
      return new DefaultApiVersionInserterBuilder(null, null, null, null);
   }

   interface Builder {
      ApiVersionInserter.Builder useHeader(@Nullable String header);

      ApiVersionInserter.Builder useQueryParam(@Nullable String queryParam);

      ApiVersionInserter.Builder useMediaTypeParam(@Nullable String param);

      ApiVersionInserter.Builder usePathSegment(@Nullable Integer pathSegmentIndex);

      ApiVersionInserter.Builder withVersionFormatter(ApiVersionFormatter versionFormatter);

      ApiVersionInserter build();
   }
}
