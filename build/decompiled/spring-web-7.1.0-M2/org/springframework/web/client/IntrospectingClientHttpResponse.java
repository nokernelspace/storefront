package org.springframework.web.client;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;

class IntrospectingClientHttpResponse extends ClientHttpResponseDecorator {
   private @Nullable PushbackInputStream pushbackInputStream;

   public IntrospectingClientHttpResponse(ClientHttpResponse response) {
      super(response);
   }

   public boolean hasMessageBody() throws IOException {
      HttpStatusCode statusCode = this.getStatusCode();
      return !statusCode.is1xxInformational() && statusCode != HttpStatus.NO_CONTENT && statusCode != HttpStatus.NOT_MODIFIED
         ? this.getHeaders().getContentLength() != 0L
         : false;
   }

   public boolean hasEmptyMessageBody() throws IOException {
      InputStream body = this.getDelegate().getBody();
      if (body == null) {
         return true;
      }

      try {
         if (body.markSupported()) {
            body.mark(1);
            if (body.read() == -1) {
               return true;
            }

            body.reset();
            return false;
         } else {
            this.pushbackInputStream = new PushbackInputStream(body);
            int b = this.pushbackInputStream.read();
            if (b == -1) {
               return true;
            }

            this.pushbackInputStream.unread(b);
            return false;
         }
      } catch (EOFException exc) {
         return true;
      }
   }

   @Override
   public InputStream getBody() throws IOException {
      return this.pushbackInputStream != null ? this.pushbackInputStream : this.getDelegate().getBody();
   }
}
