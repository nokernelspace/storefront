package org.springframework.http.client.reactive;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ReactiveHttpInputMessage;
import org.springframework.http.ResponseCookie;
import org.springframework.util.MultiValueMap;
import org.springframework.util.ObjectUtils;

public interface ClientHttpResponse extends ReactiveHttpInputMessage {
   default String getId() {
      return ObjectUtils.getIdentityHexString(this);
   }

   HttpStatusCode getStatusCode();

   MultiValueMap<String, ResponseCookie> getCookies();
}
