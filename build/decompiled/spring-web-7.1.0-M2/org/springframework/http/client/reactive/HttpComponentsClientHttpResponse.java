package org.springframework.http.client.reactive;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.cookie.CookieOrigin;
import org.apache.hc.client5.http.cookie.CookieSpec;
import org.apache.hc.client5.http.cookie.MalformedCookieException;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpMessage;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Message;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseCookie;
import org.springframework.http.support.HttpComponentsHeadersAdapter;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;

class HttpComponentsClientHttpResponse extends AbstractClientHttpResponse {
   public HttpComponentsClientHttpResponse(DataBufferFactory dataBufferFactory, Message<HttpResponse, Publisher<ByteBuffer>> message, HttpClientContext context) {
      super(
         HttpStatusCode.valueOf(((HttpResponse)message.getHead()).getCode()),
         HttpHeaders.readOnlyHttpHeaders(new HttpComponentsHeadersAdapter((HttpMessage)message.getHead())),
         adaptCookies((HttpResponse)message.getHead(), context),
         Flux.from((Publisher)message.getBody()).map(dataBufferFactory::wrap)
      );
   }

   private static MultiValueMap<String, ResponseCookie> adaptCookies(HttpResponse response, HttpClientContext context) {
      LinkedMultiValueMap<String, ResponseCookie> result = new LinkedMultiValueMap();
      CookieSpec cookieSpec = context.getCookieSpec();
      if (cookieSpec == null) {
         return result;
      }

      CookieOrigin cookieOrigin = context.getCookieOrigin();
      Iterator<Header> itr = response.headerIterator("Set-Cookie");

      while (itr.hasNext()) {
         Header header = itr.next();

         try {
            for (Cookie cookie : cookieSpec.parse(header, cookieOrigin)) {
               try {
                  cookieSpec.validate(cookie, cookieOrigin);
                  result.add(
                     cookie.getName(),
                     ResponseCookie.fromClientResponse(cookie.getName(), cookie.getValue())
                        .domain(cookie.getDomain())
                        .path(cookie.getPath())
                        .maxAge(getMaxAgeSeconds(cookie))
                        .secure(cookie.isSecure())
                        .httpOnly(cookie.containsAttribute("httponly"))
                        .partitioned(cookie.containsAttribute("partitioned"))
                        .sameSite(cookie.getAttribute("samesite"))
                        .build()
                  );
               } catch (MalformedCookieException var11) {
               }
            }
         } catch (MalformedCookieException var12) {
         }
      }

      return result;
   }

   private static long getMaxAgeSeconds(Cookie cookie) {
      String expiresAttribute = cookie.getAttribute("expires");
      String maxAgeAttribute = cookie.getAttribute("max-age");
      if (maxAgeAttribute != null) {
         return Long.parseLong(maxAgeAttribute);
      }

      if (expiresAttribute != null) {
         try {
            ZonedDateTime expiresDate = ZonedDateTime.parse(expiresAttribute, DateTimeFormatter.RFC_1123_DATE_TIME);
            return Duration.between(ZonedDateTime.now(expiresDate.getZone()), expiresDate).toSeconds();
         } catch (DateTimeParseException var4) {
         }
      }

      return -1L;
   }
}
