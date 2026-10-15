package org.springframework.web.server;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

public class MethodNotAllowedException extends ResponseStatusException {
   private final String method;
   private final Set<HttpMethod> httpMethods;

   public MethodNotAllowedException(HttpMethod method, Collection<HttpMethod> supportedMethods) {
      this(method.name(), supportedMethods);
   }

   public MethodNotAllowedException(String method, @Nullable Collection<HttpMethod> supportedMethods) {
      super(HttpStatus.METHOD_NOT_ALLOWED, "Request method '" + method + "' is not supported.", null, null, new Object[]{method, supportedMethods});
      Assert.notNull(method, "'method' is required");
      if (supportedMethods == null) {
         supportedMethods = Collections.emptySet();
      }

      this.method = method;
      this.httpMethods = Collections.unmodifiableSet(new LinkedHashSet<>(supportedMethods));
      if (!this.httpMethods.isEmpty()) {
         this.setDetail("Supported methods: " + this.httpMethods);
      }
   }

   @Override
   public HttpHeaders getHeaders() {
      if (CollectionUtils.isEmpty(this.httpMethods)) {
         return HttpHeaders.EMPTY;
      }

      HttpHeaders headers = new HttpHeaders();
      headers.setAllow(this.httpMethods);
      return headers;
   }

   public String getHttpMethod() {
      return this.method;
   }

   public Set<HttpMethod> getSupportedMethods() {
      return this.httpMethods;
   }
}
