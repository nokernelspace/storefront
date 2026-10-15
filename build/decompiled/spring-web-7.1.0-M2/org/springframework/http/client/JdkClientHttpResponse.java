package org.springframework.http.client;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.StreamUtils;

class JdkClientHttpResponse implements ClientHttpResponse {
   private final HttpResponse<InputStream> response;
   private final HttpHeaders headers;
   private final InputStream body;

   JdkClientHttpResponse(HttpResponse<InputStream> response, Consumer<HttpHeaders> headersConsumer, @Nullable InputStream body) {
      this.response = response;
      this.headers = adaptHeaders(response, headersConsumer);
      this.body = body != null ? body : InputStream.nullInputStream();
   }

   private static HttpHeaders adaptHeaders(HttpResponse<?> response, Consumer<HttpHeaders> headersConsumer) {
      Map<String, List<String>> rawHeaders = response.headers().map();
      HttpHeaders headers = new HttpHeaders();
      rawHeaders.forEach(headers::put);
      headersConsumer.accept(headers);
      return HttpHeaders.readOnlyHttpHeaders(headers);
   }

   @Override
   public HttpStatusCode getStatusCode() {
      return HttpStatusCode.valueOf(this.response.statusCode());
   }

   @Override
   public String getStatusText() {
      return this.getStatusCode() instanceof HttpStatus status ? status.getReasonPhrase() : "";
   }

   @Override
   public HttpHeaders getHeaders() {
      return this.headers;
   }

   @Override
   public InputStream getBody() throws IOException {
      return this.body;
   }

   @Override
   public void close() {
      try {
         try {
            StreamUtils.drain(this.body);
         } finally {
            this.body.close();
         }
      } catch (IOException var5) {
      }
   }
}
