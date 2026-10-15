package org.springframework.web.client;

import org.jspecify.annotations.Nullable;

final class DefaultApiVersionInserterBuilder implements ApiVersionInserter.Builder {
   private @Nullable String header;
   private @Nullable String queryParam;
   private @Nullable String mediaTypeParam;
   private @Nullable Integer pathSegmentIndex;
   private @Nullable ApiVersionFormatter versionFormatter;

   DefaultApiVersionInserterBuilder(@Nullable String header, @Nullable String queryParam, @Nullable String mediaTypeParam, @Nullable Integer pathSegmentIndex) {
      this.header = header;
      this.queryParam = queryParam;
      this.mediaTypeParam = mediaTypeParam;
      this.pathSegmentIndex = pathSegmentIndex;
   }

   @Override
   public ApiVersionInserter.Builder useHeader(@Nullable String header) {
      this.header = header;
      return this;
   }

   @Override
   public ApiVersionInserter.Builder useQueryParam(@Nullable String queryParam) {
      this.queryParam = queryParam;
      return this;
   }

   @Override
   public ApiVersionInserter.Builder useMediaTypeParam(@Nullable String param) {
      this.mediaTypeParam = param;
      return this;
   }

   @Override
   public ApiVersionInserter.Builder usePathSegment(@Nullable Integer pathSegmentIndex) {
      this.pathSegmentIndex = pathSegmentIndex;
      return this;
   }

   @Override
   public ApiVersionInserter.Builder withVersionFormatter(ApiVersionFormatter versionFormatter) {
      this.versionFormatter = versionFormatter;
      return this;
   }

   @Override
   public ApiVersionInserter build() {
      return new DefaultApiVersionInserter(this.header, this.queryParam, this.mediaTypeParam, this.pathSegmentIndex, this.versionFormatter);
   }
}
