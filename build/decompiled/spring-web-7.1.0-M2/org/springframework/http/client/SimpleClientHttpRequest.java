package org.springframework.http.client;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.StreamingHttpOutputMessage;
import org.springframework.util.StringUtils;

final class SimpleClientHttpRequest extends AbstractStreamingClientHttpRequest {
   private final HttpURLConnection connection;
   private final int chunkSize;

   SimpleClientHttpRequest(HttpURLConnection connection, int chunkSize) {
      this.connection = connection;
      this.chunkSize = chunkSize;
   }

   @Override
   public HttpMethod getMethod() {
      return HttpMethod.valueOf(this.connection.getRequestMethod());
   }

   @Override
   public URI getURI() {
      try {
         return this.connection.getURL().toURI();
      } catch (URISyntaxException ex) {
         throw new IllegalStateException("Could not get HttpURLConnection URI: " + ex.getMessage(), ex);
      }
   }

   @Override
   protected ClientHttpResponse executeInternal(HttpHeaders headers, StreamingHttpOutputMessage.@Nullable Body body) throws IOException {
      if (this.connection.getDoOutput()) {
         long contentLength = headers.getContentLength();
         if (contentLength >= 0L) {
            this.connection.setFixedLengthStreamingMode(contentLength);
         } else {
            this.connection.setChunkedStreamingMode(this.chunkSize);
         }
      }

      addHeaders(this.connection, headers);
      this.connection.connect();
      if (this.connection.getDoOutput() && body != null) {
         try (OutputStream os = this.connection.getOutputStream()) {
            body.writeTo(os);
         }
      } else {
         this.connection.getResponseCode();
      }

      return new SimpleClientHttpResponse(this.connection);
   }

   static void addHeaders(HttpURLConnection connection, HttpHeaders headers) {
      String method = connection.getRequestMethod();
      if ((method.equals("PUT") || method.equals("DELETE")) && !StringUtils.hasText(headers.getFirst("Accept"))) {
         headers.set("Accept", "*/*");
      }

      headers.forEach((headerName, headerValues) -> {
         if ("Cookie".equalsIgnoreCase(headerName)) {
            String headerValue = StringUtils.collectionToDelimitedString(headerValues, "; ");
            connection.setRequestProperty(headerName, headerValue);
         } else {
            for (String headerValue : headerValues) {
               String actualHeaderValue = headerValue != null ? headerValue : "";
               connection.addRequestProperty(headerName, actualHeaderValue);
            }
         }
      });
   }
}
