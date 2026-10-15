package org.springframework.http.server;

import java.net.URI;
import org.jspecify.annotations.Nullable;

public interface RequestPath extends PathContainer {
   PathContainer contextPath();

   PathContainer pathWithinApplication();

   RequestPath modifyContextPath(String contextPath);

   static RequestPath parse(URI uri, @Nullable String contextPath) {
      return parse(uri.getRawPath(), contextPath);
   }

   static RequestPath parse(String rawPath, @Nullable String contextPath) {
      return new DefaultRequestPath(rawPath, contextPath);
   }
}
