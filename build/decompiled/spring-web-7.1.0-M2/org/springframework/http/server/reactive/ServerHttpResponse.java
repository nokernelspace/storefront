package org.springframework.http.server.reactive;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.http.ResponseCookie;
import org.springframework.util.MultiValueMap;

public interface ServerHttpResponse extends ReactiveHttpOutputMessage {
   boolean setStatusCode(@Nullable HttpStatusCode status);

   @Nullable HttpStatusCode getStatusCode();

   default boolean setRawStatusCode(@Nullable Integer value) {
      return this.setStatusCode(value != null ? HttpStatusCode.valueOf(value) : null);
   }

   MultiValueMap<String, ResponseCookie> getCookies();

   void addCookie(ResponseCookie cookie);
}
