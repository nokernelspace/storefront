package org.springframework.http;

import java.io.Serializable;
import java.util.Locale;
import org.jspecify.annotations.Nullable;
import org.springframework.util.Assert;

public final class HttpMethod implements Comparable<HttpMethod>, Serializable {
   private static final long serialVersionUID = -70133475680645360L;
   public static final HttpMethod GET = new HttpMethod("GET");
   public static final HttpMethod HEAD = new HttpMethod("HEAD");
   public static final HttpMethod POST = new HttpMethod("POST");
   public static final HttpMethod PUT = new HttpMethod("PUT");
   public static final HttpMethod PATCH = new HttpMethod("PATCH");
   public static final HttpMethod DELETE = new HttpMethod("DELETE");
   public static final HttpMethod OPTIONS = new HttpMethod("OPTIONS");
   public static final HttpMethod TRACE = new HttpMethod("TRACE");
   public static final HttpMethod QUERY = new HttpMethod("QUERY");
   private static final HttpMethod[] values = new HttpMethod[]{GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS, TRACE, QUERY};
   private final String name;

   private HttpMethod(String name) {
      this.name = name;
   }

   public static HttpMethod[] values() {
      HttpMethod[] copy = new HttpMethod[values.length];
      System.arraycopy(values, 0, copy, 0, values.length);
      return copy;
   }

   public static HttpMethod valueOf(String method) {
      Assert.notNull(method, "Method must not be null");

      return switch (method.toUpperCase(Locale.ROOT)) {
         case "GET" -> GET;
         case "HEAD" -> HEAD;
         case "POST" -> POST;
         case "PUT" -> PUT;
         case "PATCH" -> PATCH;
         case "DELETE" -> DELETE;
         case "OPTIONS" -> OPTIONS;
         case "TRACE" -> TRACE;
         case "QUERY" -> QUERY;
         default -> new HttpMethod(method);
      };
   }

   public String name() {
      return this.name;
   }

   public boolean matches(String method) {
      return this.name().equals(method);
   }

   public int compareTo(HttpMethod other) {
      return this.name.compareTo(other.name);
   }

   @Override
   public int hashCode() {
      return this.name.hashCode();
   }

   @Override
   public boolean equals(@Nullable Object other) {
      return this == other || other instanceof HttpMethod that && this.name.equals(that.name);
   }

   @Override
   public String toString() {
      return this.name;
   }
}
