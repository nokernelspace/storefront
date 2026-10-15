package org.springframework.http;

import java.io.Serializable;
import org.springframework.util.Assert;

public sealed interface HttpStatusCode extends Serializable permits DefaultHttpStatusCode, HttpStatus {
   int value();

   boolean is1xxInformational();

   boolean is2xxSuccessful();

   boolean is3xxRedirection();

   boolean is4xxClientError();

   boolean is5xxServerError();

   boolean isError();

   default boolean isSameCodeAs(HttpStatusCode other) {
      return this.value() == other.value();
   }

   static HttpStatusCode valueOf(int code) {
      Assert.isTrue(code >= 100 && code <= 999, () -> "Status code '" + code + "' should be a three-digit positive integer");
      HttpStatus status = HttpStatus.resolve(code);
      return status != null ? status : new DefaultHttpStatusCode(code);
   }
}
