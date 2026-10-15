package org.springframework.web.bind.annotation;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpMethod;
import org.springframework.util.Assert;

public enum RequestMethod {
   GET,
   HEAD,
   POST,
   PUT,
   PATCH,
   DELETE,
   OPTIONS,
   TRACE,
   QUERY;

   public static @Nullable RequestMethod resolve(String method) {
      Assert.notNull(method, "Method must not be null");

      return switch (method) {
         case "GET" -> GET;
         case "HEAD" -> HEAD;
         case "POST" -> POST;
         case "PUT" -> PUT;
         case "PATCH" -> PATCH;
         case "DELETE" -> DELETE;
         case "OPTIONS" -> OPTIONS;
         case "TRACE" -> TRACE;
         case "QUERY" -> QUERY;
         default -> null;
      };
   }

   public static @Nullable RequestMethod resolve(HttpMethod httpMethod) {
      Assert.notNull(httpMethod, "HttpMethod must not be null");
      return resolve(httpMethod.name());
   }

   public HttpMethod asHttpMethod() {
      return switch (this) {
         case GET -> HttpMethod.GET;
         case HEAD -> HttpMethod.HEAD;
         case POST -> HttpMethod.POST;
         case PUT -> HttpMethod.PUT;
         case PATCH -> HttpMethod.PATCH;
         case DELETE -> HttpMethod.DELETE;
         case OPTIONS -> HttpMethod.OPTIONS;
         case TRACE -> HttpMethod.TRACE;
         case QUERY -> HttpMethod.QUERY;
      };
   }
}
