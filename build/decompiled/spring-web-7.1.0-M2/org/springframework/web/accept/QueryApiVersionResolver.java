package org.springframework.web.accept;

import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

public class QueryApiVersionResolver implements ApiVersionResolver {
   private final String queryParamName;

   public QueryApiVersionResolver(String queryParamName) {
      this.queryParamName = queryParamName;
   }

   @Override
   public @Nullable String resolveVersion(HttpServletRequest request) {
      String query = request.getQueryString();
      if (StringUtils.hasText(query)) {
         UriComponents uri = UriComponentsBuilder.fromUriString("?" + query).build();
         return (String)uri.getQueryParams().getFirst(this.queryParamName);
      } else {
         return null;
      }
   }
}
