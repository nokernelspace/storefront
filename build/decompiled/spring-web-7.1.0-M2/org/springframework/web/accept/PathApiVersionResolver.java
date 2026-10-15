package org.springframework.web.accept;

import jakarta.servlet.http.HttpServletRequest;
import java.util.function.Predicate;
import org.jspecify.annotations.Nullable;
import org.springframework.http.server.PathContainer;
import org.springframework.http.server.RequestPath;
import org.springframework.util.Assert;
import org.springframework.web.util.ServletRequestPathUtils;

public class PathApiVersionResolver implements ApiVersionResolver {
   private final int pathSegmentIndex;
   private @Nullable Predicate<RequestPath> versionPathPredicate;

   public PathApiVersionResolver(int pathSegmentIndex) {
      Assert.isTrue(pathSegmentIndex >= 0, "'pathSegmentIndex' must be >= 0");
      this.pathSegmentIndex = pathSegmentIndex;
   }

   public PathApiVersionResolver(int pathSegmentIndex, Predicate<RequestPath> versionPathPredicate) {
      this(pathSegmentIndex);
      this.versionPathPredicate = versionPathPredicate;
   }

   @Override
   public @Nullable String resolveVersion(HttpServletRequest request) {
      RequestPath path = ServletRequestPathUtils.getParsedRequestPath(request);
      if (!this.isVersionedPath(path)) {
         return null;
      }

      int i = 0;

      for (PathContainer.Element element : path.pathWithinApplication().elements()) {
         if (element instanceof PathContainer.PathSegment && i++ == this.pathSegmentIndex) {
            return element.value();
         }
      }

      throw new InvalidApiVersionException("No path segment at index " + this.pathSegmentIndex);
   }

   private boolean isVersionedPath(RequestPath path) {
      return this.versionPathPredicate == null || this.versionPathPredicate.test(path);
   }
}
