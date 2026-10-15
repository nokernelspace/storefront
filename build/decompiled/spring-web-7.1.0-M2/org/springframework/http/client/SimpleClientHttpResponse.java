package org.springframework.http.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

final class SimpleClientHttpResponse implements ClientHttpResponse {
   private final HttpURLConnection connection;
   private @Nullable HttpHeaders headers;
   private @Nullable InputStream responseStream;

   SimpleClientHttpResponse(HttpURLConnection connection) {
      this.connection = connection;
   }

   @Override
   public HttpStatusCode getStatusCode() throws IOException {
      return HttpStatusCode.valueOf(this.connection.getResponseCode());
   }

   @Override
   public String getStatusText() throws IOException {
      String result = this.connection.getResponseMessage();
      return result != null ? result : "";
   }

   @Override
   public HttpHeaders getHeaders() {
      if (this.headers == null) {
         this.headers = new HttpHeaders();
         String name = this.connection.getHeaderFieldKey(0);
         if (StringUtils.hasLength(name)) {
            this.headers.add(name, this.connection.getHeaderField(0));
         }

         int i = 1;

         while (true) {
            name = this.connection.getHeaderFieldKey(i);
            if (!StringUtils.hasLength(name)) {
               break;
            }

            this.headers.add(name, this.connection.getHeaderField(i));
            i++;
         }
      }

      return this.headers;
   }

   @Override
   public InputStream getBody() throws IOException {
      if (this.responseStream == null) {
         if (this.connection.getResponseCode() >= 400) {
            InputStream errorStream = this.connection.getErrorStream();
            this.responseStream = errorStream != null ? errorStream : InputStream.nullInputStream();
         } else {
            this.responseStream = this.connection.getInputStream();
         }
      }

      return this.responseStream;
   }

   @Override
   public void close() {
      try {
         if (this.responseStream == null) {
            this.getBody();
         }

         Objects.requireNonNull(this.responseStream);
         StreamUtils.drain(this.responseStream);
         this.responseStream.close();
      } catch (Exception var2) {
      }
   }
}
