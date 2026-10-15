package org.springframework.http.client.reactive;

import java.net.URI;
import java.util.Map;
import org.springframework.http.HttpCookie;
import org.springframework.http.HttpMethod;
import org.springframework.http.ReactiveHttpOutputMessage;
import org.springframework.util.MultiValueMap;

public interface ClientHttpRequest extends ReactiveHttpOutputMessage {
   HttpMethod getMethod();

   URI getURI();

   MultiValueMap<String, HttpCookie> getCookies();

   Map<String, Object> getAttributes();

   <T> T getNativeRequest();
}
