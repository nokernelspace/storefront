package org.springframework.http.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.StreamUtils;

final class BufferingClientHttpResponseWrapper implements ClientHttpResponse {
   private final ClientHttpResponse response;
   private volatile byte @Nullable [] body;

   BufferingClientHttpResponseWrapper(ClientHttpResponse response) {
      this.response = response;
   }

   @Override
   public HttpStatusCode getStatusCode() throws IOException {
      return this.response.getStatusCode();
   }

   @Override
   public String getStatusText() throws IOException {
      return this.response.getStatusText();
   }

   @Override
   public HttpHeaders getHeaders() {
      return this.response.getHeaders();
   }

   @Override
   public InputStream getBody() throws IOException {
      byte[] body = this.body;
      if (body == null) {
         synchronized (this) {
            body = this.body;
            if (body == null) {
               body = StreamUtils.copyToByteArray(this.response.getBody());
               this.body = body;
            }
         }
      }

      return new ByteArrayInputStream(body);
   }

   @Override
   public void close() {
      this.response.close();
   }
}
